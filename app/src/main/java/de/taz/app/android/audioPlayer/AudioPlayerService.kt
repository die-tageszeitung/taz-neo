package de.taz.app.android.audioPlayer

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val SEEK_FORWARD_MS = 15000L
private const val SEEK_BACKWARD_MS = 15000L

/**
 * [AudioPlayerService] will be shared between all activities.
 *
 * It holds the audio player's main state and allows to control the playing of article audios.
 * It connects to the [ArticleAudioMediaSessionService] and wraps its functionality.
 */
class AudioPlayerService private constructor(private val applicationContext: Context) :
    CoroutineScope {

    companion object : SingletonHolder<AudioPlayerService, Context>(::AudioPlayerService)

    /** Internal states of the [AudioPlayerService] */
    private sealed class State {
        object Init : State()
        data class ControllerReady(val controller: MediaController) : State()
        data class ControllerError(val exception: Exception) : State()
        data class AudioQueued(val articleAudio: ArticleAudio) : State()
        data class AudioPrepare(
            val controller: MediaController,
            val articleAudio: ArticleAudio
        ) : State()

        data class AudioReady(
            val controller: MediaController,
            val articleAudio: ArticleAudio
        ) : State()

        data class AudioPlaying(
            val controller: MediaController,
            val articleAudio: ArticleAudio
        ) : State()

        data class AudioError(
            val controller: MediaController,
            val articleAudio: ArticleAudio,
            val exception: PlaybackException,
        ) : State()
    }

    private val log by Log

    // Use the Main dispatcher as the default because all calls to the controller have to be from the Main thread
    private val coroutineJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineJob + Dispatchers.Main

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val tracker = Tracker.getInstance(applicationContext)

    // Central internal state of the Service
    private val state: MutableStateFlow<State> = MutableStateFlow(State.Init)
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Hidden)

    private val _progress = MutableStateFlow<PlayerProgress?>(null)
    private var progressObserverJob: Job? = null

    // region public attributes and methods
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val progress: StateFlow<PlayerProgress?> = _progress.asStateFlow()

    fun playArticleAudioAsync(articleStub: ArticleStub): Deferred<Unit> {
        return async {
            val article = articleRepository.get(articleStub.articleFileName)
            if (article != null) {
                playArticleAudioAsync(article).await()
            } else {
                throw AudioPlayerException.Generic("Could not load the full Article for ArticleStub(articleFileName=${articleStub.articleFileName})")
            }
        }
    }

    fun playArticleAudioAsync(article: Article): Deferred<Unit> {
        return async {
            val audioFile = article.audioFile

            val sectionStub = article.getSectionStub(applicationContext)
            val issueStub = sectionStub?.getIssueStub(applicationContext)

            if (issueStub != null && sectionStub != null && audioFile != null) {
                val audioFileUrl = createAudioFileUrl(issueStub, audioFile)
                val articleAudio = ArticleAudio(audioFileUrl, article, issueStub.issueKey)
                enqueueAndPlay(articleAudio)
            } else {
                throw AudioPlayerException.Generic("Could not load audio data for the Article(key=${article.key})")
            }
        }
    }

    fun toggleAudioPlaying() {
        when (val state = state.value) {
            // Try to re-prepare and play the audio
            is State.AudioError -> enqueueAndPlay(state.articleAudio)

            // Let the audio controller decide how to pause/play the current audio
            is State.AudioPlaying -> toggleAudioControllerPlaying(state.controller)
            is State.AudioPrepare -> toggleAudioControllerPlaying(state.controller)
            is State.AudioReady -> toggleAudioControllerPlaying(state.controller)

            // Ignore: no known audio is queued
            State.Init, is State.ControllerReady, is State.ControllerError -> Unit

            // Ignore: the controller is not ready yet, so we can't easily control playing.
            // This is an edge case as the UI for pausing should not be shown without a controller
            // being ready.
            is State.AudioQueued -> Unit
        }
    }

    fun dismissPlayer() {
        val controller = getControllerFromState()
        controller?.apply {
            stop()
            onControllerDismiss(this)
        }
        forceState(State.Init)
    }

    fun onErrorHandled(error: UiState.Error) {
        _uiState.compareAndSet(error, error.copy(wasHandled = true))
    }

    fun setPlayerExpanded(expanded: Boolean) {
        var updated = false
        while (!updated) {
            val prevState = _uiState.value
            val newState = when(prevState) {
                is UiState.Error -> prevState.copy(expanded = expanded)
                is UiState.Paused -> prevState.copy(expanded = expanded)
                is UiState.Playing -> prevState.copy(expanded = expanded)
                UiState.Hidden, is UiState.Loading -> prevState
            }
            updated = _uiState.compareAndSet(prevState, newState)
        }
    }

    fun seekTo(positionMs: Long) {
        getControllerFromState()?.apply {
            this.seekTo(positionMs)
        }
    }

    fun seekForward() {
        getControllerFromState()?.apply {
            val newPosition = (currentPosition + SEEK_FORWARD_MS)
            if (newPosition < duration) {
                seekTo(newPosition)
            }
        }
    }

    fun seekBackward() {
        getControllerFromState()?.apply {
            val newPosition = (currentPosition - SEEK_BACKWARD_MS).coerceAtLeast(0L)
            seekTo(newPosition)
        }
    }
    // endregion public attributes and methods

    init {
        // Custom coroutine to map [State] to [UiState].
        // We can't use .map().stateIn() because we need a MutableStateFlow to be able to
        // get the current subscriber count and decide if we can release the [MediaController]
        launch {
            state.collect {
                val wasExpanded = getExpandedFromUiState()
                val uiState = when (val state = it) {
                    State.Init -> UiState.Hidden
                    is State.ControllerReady -> UiState.Hidden

                    is State.ControllerError -> UiState.Error(
                        expanded = wasExpanded,
                        wasHandled = false,
                        articleAudio = null,
                        AudioPlayerException.Generic(cause = state.exception)
                    )

                    is State.AudioPrepare -> UiState.Loading(state.articleAudio)
                    is State.AudioQueued -> UiState.Loading(state.articleAudio)
                    is State.AudioReady -> UiState.Paused(state.articleAudio, wasExpanded)
                    is State.AudioPlaying -> UiState.Playing(state.articleAudio, wasExpanded)
                    is State.AudioError -> UiState.Error(
                        expanded = wasExpanded,
                        wasHandled = false,
                        state.articleAudio,
                        mapAudioErrorToException(state)
                    )
                }
                _uiState.value = uiState
            }
        }

        // Trigger tracking events from the default dispatcher
        launch(Dispatchers.Default) {
            state.collect {state ->
                when(state) {
                    is State.AudioPlaying -> tracker.trackAudioPlayerPlayArticleEvent(state.articleAudio)
                    else -> Unit
                }
            }
        }
    }

    private fun createAudioFileUrl(issueStub: IssueStub, audioFile: FileEntry): Uri {
        return Uri.parse("${issueStub.baseUrl}/${audioFile.name}")
    }

    private fun enqueueAndPlay(articleAudio: ArticleAudio) {
        log.verbose("enqueueAndPlay(${articleAudio.article.key})")
        var updated = false
        var prevState: State? = null
        var newState: State? = null

        while (!updated) {
            prevState = state.value
            newState = when (prevState) {
                // Retry with this articleAudio file
                is State.AudioError -> State.AudioPrepare(prevState.controller, articleAudio)
                // Overwrite the current preparation
                is State.AudioPrepare -> State.AudioPrepare(prevState.controller, articleAudio)
                is State.AudioReady -> State.AudioPrepare(prevState.controller, articleAudio)
                is State.AudioPlaying -> State.AudioPrepare(prevState.controller, articleAudio)
                is State.ControllerReady -> State.AudioPrepare(prevState.controller, articleAudio)
                // Overwrite the queued Audio
                is State.AudioQueued -> State.AudioQueued(articleAudio)
                is State.ControllerError, State.Init -> State.AudioQueued(articleAudio)
            }
            updated = compareAndSetState(prevState, newState)
        }

        // Trigger operations required for the transition
        val controllerNeedsConnection =
            prevState is State.ControllerError || prevState is State.Init
        when {
            newState is State.AudioPrepare ->
                prepareAudio(newState.controller, newState.articleAudio)

            newState is State.AudioQueued && controllerNeedsConnection ->
                connectController()

            else -> Unit
        }
    }

    private fun connectController() {
        log.verbose("Connecting MediaController")
        launch {
            val sessionToken =
                SessionToken(
                    applicationContext,
                    ComponentName(applicationContext, ArticleAudioMediaSessionService::class.java)
                )
            val controllerFuture =
                MediaController.Builder(applicationContext, sessionToken).buildAsync()

            try {
                val controller = controllerFuture.await()
                onControllerReady(controller)
            } catch (e: Exception) {
                forceState(State.ControllerError(e))
            }
        }
    }

    private fun onControllerReady(controller: MediaController) {
        log.verbose("onControllerReady (${controller.hashCode()}")
        controller.addListener(controllerListener)
        when (val state = state.value) {
            State.Init, is State.ControllerError -> {
                if (controller.currentMediaItem != null) {
                    log.error("Audio was playing without the AudioPlayerService being ready: (${controller.currentMediaItem?.mediaId})")
                    controller.apply {
                        stop()
                        clearMediaItems()
                    }
                }
                forceState(State.ControllerReady(controller))
            }

            is State.AudioQueued -> {
                forceState(State.AudioPrepare(controller, state.articleAudio))
                prepareAudio(controller, state.articleAudio)
            }
            // illegal states
            is State.ControllerReady, is State.AudioPrepare, is State.AudioError, is State.AudioReady, is State.AudioPlaying ->
                throw IllegalStateException("${state.toLogString()} not allowed onControllerReady")
        }

        launchProgressObserver()
    }

    private fun onControllerDismiss(controller: MediaController) {
        progressObserverJob?.cancel()
        controller.apply {
            clearMediaItems()
            removeListener(controllerListener)
            release()
        }
    }

    private fun prepareAudio(controller: MediaController, articleAudio: ArticleAudio) {
        log.verbose("Preparing Audio: ${articleAudio.article.key}")
        val article = articleAudio.article
        val authorList = article.authorList.distinct()
        val authors = authorList.mapNotNull { it.name }.joinToString(", ")

        val articleImage = article.imageList.firstOrNull()
        val articleImageUriString = articleImage?.let { storageService.getFileUri(it) }
        val articleImageUri = articleImageUriString?.let { Uri.parse(it) }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(article.title)
            .setArtist(authors)
            .setArtworkUri(articleImageUri)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(articleAudio.article.key)
            .setArticleAudioRequestMetadata(articleAudio)
            .setMediaMetadata(mediaMetadata)
            .build()

        controller.apply {
            playWhenReady = true
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    private fun toggleAudioControllerPlaying(controller: MediaController) {
        controller.apply {
            when (playbackState) {
                Player.STATE_READY ->
                    if (isPlaying) {
                        pause()
                    } else {
                        play()
                    }

                Player.STATE_BUFFERING -> {
                    if (playWhenReady) {
                        pause()
                    } else {
                        play()
                    }
                }

                // Ignore: the player should be hidden once the audio has ended. see onAudioEnded
                Player.STATE_ENDED -> Unit

                // Ignore: the play/pause button should not be shown while there is not audio prepared
                Player.STATE_IDLE -> Unit
            }
        }
    }

    private fun launchProgressObserver() {
        progressObserverJob?.cancel()
        progressObserverJob = launch {
            while (true) {
                val controller = getControllerFromState()
                _progress.value = controller?.let {
                    val current = it.currentPosition
                    val duration = it.duration

                    if (duration > 0) {
                        PlayerProgress(current, duration)
                    } else {
                        null
                    }
                }
                delay(200)
            }
        }
    }

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                onAudioPlay()
            } else {
                onAudioPause()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            onAudioError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> onAudioEnded()
                Player.STATE_BUFFERING, Player.STATE_IDLE, Player.STATE_READY -> Unit
            }
        }
    }

    private fun onAudioPlay() {
        val newState = when (val state = state.value) {
            is State.AudioError -> State.AudioPlaying(state.controller, state.articleAudio)
            is State.AudioPlaying -> State.AudioPlaying(state.controller, state.articleAudio)
            is State.AudioReady -> State.AudioPlaying(state.controller, state.articleAudio)
            is State.AudioPrepare -> State.AudioPlaying(state.controller, state.articleAudio)

            State.Init, is State.AudioQueued, is State.ControllerError, is State.ControllerReady -> null
        }

        if (newState is State.AudioPlaying) {
            // Ensure the same article is playing
            val playingArticleFileName = newState.controller.currentMediaItem?.mediaId
            if (playingArticleFileName == newState.articleAudio.article.key) {
                forceState(newState)
            } else {
                log.warn("Android AudioPlayer has prepared another audio. Trigger reloading of requested articleAudio")
                enqueueAndPlay(newState.articleAudio)
            }
        }

        // setup attached activities
    }

    private fun onAudioPause() {
        val newState = when (val state = state.value) {
            is State.AudioError -> State.AudioReady(state.controller, state.articleAudio)
            is State.AudioPlaying -> State.AudioReady(state.controller, state.articleAudio)
            is State.AudioReady -> State.AudioReady(state.controller, state.articleAudio)
            is State.AudioPrepare -> State.AudioReady(state.controller, state.articleAudio)

            State.Init, is State.AudioQueued, is State.ControllerError, is State.ControllerReady -> null
        }

        if (newState is State.AudioReady) {
            // Ensure the same article is playing
            val playingArticleFileName = newState.controller.currentMediaItem?.mediaId
            if (playingArticleFileName == newState.articleAudio.article.key) {
                forceState(newState)
            } else {
                log.warn("Android AudioPlayer has prepared another audio. Trigger reloading of requested articleAudio")
                enqueueAndPlay(newState.articleAudio)
            }
        }
    }

    private fun onAudioError(error: PlaybackException) {
        log.info("Error on playing Audio: $error.errorCodeName}", error)
        val newState = when (val state = state.value) {
            is State.AudioError -> State.AudioError(state.controller, state.articleAudio, error)
            is State.AudioPlaying -> State.AudioError(state.controller, state.articleAudio, error)
            is State.AudioPrepare -> State.AudioError(state.controller, state.articleAudio, error)
            is State.AudioReady -> State.AudioError(state.controller, state.articleAudio, error)
            is State.AudioQueued, is State.ControllerError, is State.ControllerReady, State.Init -> null
        }

        if (newState is State.AudioError) {
            val playingArticleFileName = newState.controller.currentMediaItem?.mediaId
            if (playingArticleFileName == newState.articleAudio.article.key) {
                forceState(newState)
            } else {
                log.warn("Android AudioPlayer has prepared another audio. Trigger reloading of requested articleAudio")
                enqueueAndPlay(newState.articleAudio)
            }
        }
    }

    private fun onAudioEnded() {
        // For the first iteration the player shall always be deactivated once the audio has ended
        dismissPlayer()
        //        val subscriptionCount = _uiState.subscriptionCount.value
        //        val hasActiveSubscribers = (subscriptionCount > 0)
        //        log.verbose("onAudioEnded: subscriptionCount=$subscriptionCount")
        //
        //        if (!hasActiveSubscribers) {
        //            dismissPlayer()
        //        }
    }

    // region helper functions
    /** See [MutableStateFlow.compareAndSet] */
    private fun compareAndSetState(expect: State, state: State): Boolean {
        val updated = this.state.compareAndSet(expect, state)
        if (updated) {
            log.verbose("compareAndSetState: SUCCESS\n\t${expect.toLogString()}\n\t${state.toLogString()}")
        } else {
            log.verbose("compareAndSetState: FAILED\n\t${expect.toLogString()}\n\t${state.toLogString()}\n\t${this.state.value.toLogString()}")
        }
        return updated
    }

    private fun forceState(state: State) {
        log.verbose("forceState\n\t${this.state.value.toLogString()}\n\t${state.toLogString()}")
        this.state.value = state
    }

    private fun getControllerFromState(): MediaController? {
        return when (val state = state.value) {
            is State.AudioError -> state.controller
            is State.AudioPlaying -> state.controller
            is State.AudioPrepare -> state.controller
            is State.AudioReady -> state.controller
            is State.ControllerReady -> state.controller
            State.Init, is State.ControllerError, is State.AudioQueued -> null
        }
    }

    private fun State.toLogString(): String = when (this) {
        is State.AudioError -> "${this::class.simpleName}(${controller.hashCode()}, ${articleAudio.article.key})"
        is State.AudioPlaying -> "${this::class.simpleName}(${controller.hashCode()}, ${articleAudio.article.key})"
        is State.AudioPrepare -> "${this::class.simpleName}(${controller.hashCode()}, ${articleAudio.article.key})"
        is State.AudioQueued -> "${this::class.simpleName}(${articleAudio.article.key})"
        is State.AudioReady -> "${this::class.simpleName}(${controller.hashCode()}, ${articleAudio.article.key})"
        is State.ControllerError -> "${this::class.simpleName}: ${this.exception}"
        is State.ControllerReady -> "${this::class.simpleName}(${controller.hashCode()})"
        State.Init -> "${this::class.simpleName}"
    }

    private fun mapAudioErrorToException(audioError: State.AudioError): AudioPlayerException {
        return when (audioError.exception.errorCode) {
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                AudioPlayerException.Network(cause = audioError.exception)

            else -> AudioPlayerException.Generic(cause = audioError.exception)
        }
    }

    private fun getExpandedFromUiState(): Boolean {
        return when (val state = uiState.value) {
            is UiState.Error -> state.expanded
            UiState.Hidden -> false
            is UiState.Loading -> false
            is UiState.Paused -> state.expanded
            is UiState.Playing -> state.expanded
        }
    }
    // endregion helper functions
}
