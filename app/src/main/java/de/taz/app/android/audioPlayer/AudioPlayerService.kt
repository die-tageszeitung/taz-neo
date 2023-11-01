package de.taz.app.android.audioPlayer

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.DiscontinuityReason
import androidx.media3.common.Player.MediaItemTransitionReason
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.taz.app.android.DEFAULT_AUDIO_PLAYBACK_SPEED
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.dataStore.AudioPlayerDataStore
import de.taz.app.android.persistence.repository.ArticleRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val SEEK_FORWARD_MS = 15000L
private const val SEEK_BACKWARD_MS = 15000L
// Time in ms after the last break, during which a backwards seek will go to the break before the last break.
private const val SEEK_BACKWARDS_BREAK_MARGIN_MS = 1000L

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
        data class AudioQueued(val item: AudioPlayerItem) : State()

        data class AudioPrepare(
            val controller: MediaController,
            val item: AudioPlayerItem,
        ) : State()

        data class AudioReady(
            val controller: MediaController,
            val item: AudioPlayerItem,
        ) : State()

        data class AudioPlaying(
            val controller: MediaController,
            val item: AudioPlayerItem,
        ) : State()

        data class AudioError(
            val controller: MediaController,
            val item: AudioPlayerItem,
            val exception: PlaybackException,
        ) : State()
    }

    private val log by Log

    // Use the Main dispatcher as the default because all calls to the controller have to be from the Main thread
    private val coroutineJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineJob + Dispatchers.Main

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val tracker = Tracker.getInstance(applicationContext)
    private val dataStore = AudioPlayerDataStore.getInstance(applicationContext)

    private val uiStateHelper = UiStateHelper(applicationContext)
    private val mediaItemHelper = MediaItemHelper(applicationContext, uiStateHelper)

    // Central internal state of the Service
    private val state: MutableStateFlow<State> = MutableStateFlow(State.Init)
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Hidden)

    private val _progress = MutableStateFlow<PlayerProgress?>(null)
    private var progressObserverJob: Job? = null

    private val playbackSpeedPreference = dataStore.playbackSpeed.asFlow().distinctUntilChanged()
    private var playbackSpeed: Float = DEFAULT_AUDIO_PLAYBACK_SPEED

    // Store the autoPlay preference as a StateFlow, so that we can access its value without a coroutine
    private val autoPlayNextPreference =
        dataStore.autoPlayNext.asFlow().stateIn(this, SharingStarted.Eagerly, false)

    // region public attributes and methods
    val currentItem: Flow<AudioPlayerItem?> = state.map { it.getItemOrNull() }
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val progress: StateFlow<PlayerProgress?> = _progress.asStateFlow()

    fun playIssueAsync(issue: Issue): Deferred<Unit> {
        return async {
            val articlesWithAudio = issue.getArticles().filter { it.audio != null }
            val issueAudio = IssueAudio(
                IssueStub(issue),
                articlesWithAudio,
                0,
                0
            )
            enqueueAndPlay(issueAudio)
        }
    }

    fun playIssueAsync(issueStub: IssueStub, articleStub: ArticleStub): Deferred<Unit> {
        return async {
            // FIXME (johannes): This nested db/mapping call takes about 3s on the Pixel 6a and results in a visible delay after clicking on the tab bar
            val articles = articleRepository.getArticleListForIssue(issueStub.issueKey)
            val articlesWithAudio = articles.filter { it.audio != null }
            val indexOfArticle = articlesWithAudio.indexOfFirst { it.key == articleStub.key }.coerceAtLeast(0)
            val issueAudio = IssueAudio(
                issueStub,
                articlesWithAudio,
                indexOfArticle,
                indexOfArticle,
            )
            enqueueAndPlay(issueAudio)
        }
    }

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
            val sectionStub = article.getSectionStub(applicationContext)
            val issueStub = sectionStub?.getIssueStub(applicationContext)

            if (issueStub != null && article.audio != null) {
                val articleAudio = ArticleAudio(issueStub, article)
                enqueueAndPlay(articleAudio)
            } else {
                throw AudioPlayerException.Generic("Could not load audio data for the Article(key=${article.key})")
            }
        }
    }

    fun playPodcastAsync(issueStub: IssueStub, section: Section, audio: Audio): Deferred<Unit> {
        return async {
            val podcastAudio = PodcastAudio(
                issueStub,
                section,
                section.getHeaderTitle(),
                audio
            )
            enqueueAndPlay(podcastAudio)
        }
    }

    fun toggleAudioPlaying() {
        when (val state = state.value) {
            // Try to re-prepare and play the audio
            is State.AudioError -> enqueueAndPlay(state.item)

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

    fun onErrorHandled(error: UiState.InitError) {
        dismissPlayer()
    }

    fun setPlayerExpanded(expanded: Boolean) {
        if (expanded) {
            tracker.trackAudioPlayerMaximizeEvent()
        } else {
            tracker.trackAudioPlayerMinimizeEvent()
        }

        var updated = false
        while (!updated) {
            val prevState = _uiState.value
            val newState = prevState.copyWithExpanded(expanded)
            updated = _uiState.compareAndSet(prevState, newState)
        }
    }

    fun seekTo(positionMs: Long) {
        tracker.trackAudioPlayerSeekPositionEvent()
        getControllerFromState()?.apply {
            this.seekTo(positionMs)
        }
    }

    /**
     * Seek forward within the currently active audio.
     * If the respective [AudioPlayerItem] has some breaks defined it will seek to the next break.
     * If not, it will seek [SEEK_FORWARD_MS] forward.
     */
    fun seekForward() {
        val breaks = getItemFromState()?.breaks

        if (!breaks.isNullOrEmpty()) {
            tracker.trackAudioPlayerSeekForwardBreakEvent()
            getControllerFromState()?.apply {
                val currentPositionInS = currentPosition / 1000F
                val nextBreakInS = breaks.find { it > currentPositionInS }
                val nextBreakPositionInMs = nextBreakInS?.times(1000L)?.toLong()

                val nextBreakPositionOrEndInMs = nextBreakPositionInMs?.coerceAtMost(duration) ?: duration
                seekTo(nextBreakPositionOrEndInMs)
            }
        } else {
            tracker.trackAudioPlayerSeekForwardSecondsEvent(SEEK_FORWARD_MS / 1_000L)
            getControllerFromState()?.apply {
                val newPosition = (currentPosition + SEEK_FORWARD_MS)
                seekTo(newPosition.coerceAtMost(duration))
            }
        }
    }

    /**
     * Seek backwards within the currently active audio.
     * If the respective [AudioPlayerItem] has some breaks defined it will seek to the previous break.
     * If not, it will seek [SEEK_BACKWARD_MS] backwards.
     */
    fun seekBackward() {
        val breaks = getItemFromState()?.breaks

        if (!breaks.isNullOrEmpty()) {
            tracker.trackAudioPlayerSeekBackwardBreakEvent()
            getControllerFromState()?.apply {
                val currentPositionWithSlack = (currentPosition - SEEK_BACKWARDS_BREAK_MARGIN_MS).coerceAtLeast(0L)
                val currentPositionWithSlackInS = currentPositionWithSlack / 1000F
                val prevBreakInS = breaks.findLast { it <= currentPositionWithSlackInS }
                val prevBreakPositionInMs = prevBreakInS?.times(1000L)?.toLong()

                val prevBreakPositionOrStartInMs = prevBreakPositionInMs?.coerceAtLeast(0L) ?: 0L
                seekTo(prevBreakPositionOrStartInMs)
            }
        } else {
            tracker.trackAudioPlayerSeekBackwardSecondsEvent(SEEK_FORWARD_MS / 1_000L)
            getControllerFromState()?.apply {
                val newPosition = (currentPosition - SEEK_BACKWARD_MS)
                seekTo(newPosition.coerceAtLeast(0L))
            }
        }
    }

    fun skipToNext() {
        tracker.trackAudioPlayerSkipNextEvent()
        getControllerFromState()?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        tracker.trackAudioPlayerSkipPreviousEvent()
        getControllerFromState()?.seekToPreviousMediaItem()
    }

    suspend fun setPlaybackSpeed(playbackSpeed: Float) {
        tracker.trackAudioPlayerChangePlaySpeedEvent(playbackSpeed)
        // Only set the playback speed on the dataStore - setting the playback speed on the controller
        // will be handled by an observer on the dataStore entry.
        dataStore.playbackSpeed.set(playbackSpeed)
    }

    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    fun setAutoPlayNext(autoPlayNext: Boolean) {
        launch {
            if (autoPlayNext != autoPlayNextPreference.value) {
                if (autoPlayNext) {
                    tracker.trackAudioPlayerAutoplayEnableEvent()
                } else {
                    tracker.trackAudioPlayerAutoplayDisableEvent()
                }

                dataStore.autoPlayNext.set(autoPlayNext)
            }
        }
    }
    // endregion public attributes and methods

    init {
        // Custom coroutine to map [State] to [UiState].
        // We can't use .map().stateIn() because we need a MutableStateFlow to be able to
        // get the current subscriber count and decide if we can release the [MediaController]
        launch {
            combine(state, playbackSpeedPreference, autoPlayNextPreference) { state, playbackSpeed, autoPlayNext ->
                val wasExpanded = _uiState.value.isExpanded()
                mapUiState(state, wasExpanded, playbackSpeed, autoPlayNext)
            }.collect {
                _uiState.value = it
            }
        }

        // Trigger tracking if a different article is played
        launch(Dispatchers.Default) {
            var lastItemTracked: AudioPlayerItem? = null
            state.collect {state ->
                when(state) {
                    is State.AudioPlaying -> {
                        if (lastItemTracked != state.item) {
                            trackAudioPlaying(state.item)
                            lastItemTracked = state.item
                        }
                    }
                    is State.Init -> lastItemTracked = null
                    else -> Unit
                }
            }
        }

        launch {
            playbackSpeedPreference.collect {
                val currentController = getControllerFromState()
                currentController?.setPlaybackSpeed(it)
                playbackSpeed = it
            }
        }

        launch {
            autoPlayNextPreference.collect {
                getControllerFromState()?.setAutoPlayNext(it)
            }
        }
    }

    private fun enqueueAndPlay(item: AudioPlayerItem) {
        log.verbose("enqueueAndPlay($item)")
        var updated = false
        var prevState: State? = null
        var newState: State? = null

        while (!updated) {
            prevState = state.value
            newState = when (prevState) {
                // Retry with this articleAudio file
                is State.AudioError -> State.AudioPrepare(prevState.controller, item)
                // Overwrite the current preparation
                is State.AudioPrepare -> State.AudioPrepare(prevState.controller, item)
                is State.AudioReady -> State.AudioPrepare(prevState.controller, item)
                is State.AudioPlaying -> State.AudioPrepare(prevState.controller, item)
                is State.ControllerReady -> State.AudioPrepare(prevState.controller, item)
                // Overwrite the queued Audio
                is State.AudioQueued -> State.AudioQueued(item)
                is State.ControllerError, State.Init -> State.AudioQueued(item)
            }
            updated = compareAndSetState(prevState, newState)
        }

        // Trigger operations required for the transition
        val controllerNeedsConnection =
            prevState is State.ControllerError || prevState is State.Init
        when {
            newState is State.AudioPrepare -> launch {
                prepareAudio(newState.controller, newState.item)
            }

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

    private suspend fun onControllerReady(controller: MediaController) {
        log.verbose("onControllerReady (${controller.hashCode()}")
        controller.apply {
            addListener(controllerListener)
            setPlaybackSpeed(playbackSpeed)
        }

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
                forceState(State.AudioPrepare(controller, state.item))
                prepareAudio(controller, state.item)
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

    private suspend fun prepareAudio(controller: MediaController, item: AudioPlayerItem) {
        when (item) {
            is ArticleAudio -> prepareArticleAudio(controller, item)
            is IssueAudio -> prepareIssueAudio(controller, item)
            is PodcastAudio -> preparePodcastAudio(controller, item)
        }
    }

    private suspend fun prepareArticleAudio(controller: MediaController, articleAudio: ArticleAudio) {
        log.verbose("Preparing Article Audio: $articleAudio")
        val mediaItems = mediaItemHelper.getMediaItems(articleAudio)
        controller.apply {
            playWhenReady = true
            setMediaItems(mediaItems)
            repeatMode = REPEAT_MODE_OFF
            prepare()
            play()
        }
    }

    private suspend fun prepareIssueAudio(controller: MediaController, issueAudio: IssueAudio) {
        log.verbose("Preparing Issue Audio: $issueAudio")
        val mediaItems = mediaItemHelper.getMediaItems(issueAudio)
        controller.apply {
            playWhenReady = true
            setMediaItems(mediaItems, issueAudio.startIndex, 0L)
            setAutoPlayNext(autoPlayNextPreference.value)
            prepare()
            play()
        }
    }

    private suspend fun preparePodcastAudio(controller: MediaController, podcastAudio: PodcastAudio) {
        log.verbose("Preparing Podcast Audio: $podcastAudio")
        val mediaItems = mediaItemHelper.getMediaItems(podcastAudio)
        controller.apply {
            playWhenReady = true
            setMediaItems(mediaItems, 0, 0L)
            repeatMode = REPEAT_MODE_OFF
            prepare()
            play()
        }
    }

    private fun MediaController.setAutoPlayNext(isAutoPlayNext: Boolean) {
        repeatMode = if (isAutoPlayNext) {
            REPEAT_MODE_ALL
        } else {
            // To stop after the current audio, while still being able to skip manually,
            // we set the repeat mode to REPEAT_MODE_ONE and pause the player if we detect it
            REPEAT_MODE_ONE
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

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            @DiscontinuityReason reason: Int
        ) {
            // To be able to respect the [autoPlayNext] functionality, we set the player repeat
            // mode to [REPEAT_MODE_ONE] and immediately pause when we detect the repeat of item due
            // to an auto transition to the start of the audio.
            // Note: This is a workaround for MEDIA_ITEM_TRANSITION_REASON_REPEAT which is
            // unfortunately not triggered for REPEAT_MODE_ONE.
            if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION
                && oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                && newPosition.positionMs == 0L
                && oldPosition.positionMs != 0L) {
                getControllerFromState()?.pause()
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            @MediaItemTransitionReason reason: Int
        ) {
            if (mediaItem == null) {
                // The playlist became empty
                return
            }

            when (reason) {
                // A new [AudioPlayerItem] is being played on a new playlist
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> return
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> onMediaItemSeek(mediaItem, true)
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> onMediaItemSeek(mediaItem, false)
                // Ignored, as Android does not trigger it for REPEAT_MODE_ONE
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> Unit
            }
        }
    }

    private fun onAudioPlay() {
        val currentState = state.value
        val controller = currentState.getControllerOrNull()
        val item = currentState.getItemOrNull()
        val mediaItem = controller?.currentMediaItem

        if (controller != null && item != null && mediaItem != null) {
            if (mediaItemHelper.containsMediaItem(item, mediaItem)) {
                val newItem = mediaItemHelper.copyWithCurrentMediaItem(item, mediaItem)
                val newState = State.AudioPlaying(controller, newItem)
                forceState(newState)
            } else {
                log.warn("Android AudioPlayer has prepared another audio. Trigger reloading of requested articleAudio")
                enqueueAndPlay(item)
            }
        }
    }

    private fun onAudioPause() {
        val currentState = state.value
        val controller = currentState.getControllerOrNull()
        val item = currentState.getItemOrNull()
        val mediaItem = controller?.currentMediaItem

        if (controller != null && item != null && mediaItem != null) {
            if (mediaItemHelper.containsMediaItem(item, mediaItem)) {
                val newItem = mediaItemHelper.copyWithCurrentMediaItem(item, mediaItem)
                val newState = State.AudioReady(controller, newItem)
                forceState(newState)
            } else {
                // FIXME: error and close player instead?
                // This fallback is a last resort to sync the player and service state.
                // As this should never happen, we try to keep the code simple and just play the
                // services audio file again, even though we came from a pause.
                log.warn("Android AudioPlayer has prepared another audio. Trigger reloading of requested articleAudio")
                enqueueAndPlay(item)
            }
        }
    }

    private fun onAudioError(error: PlaybackException) {
        log.info("Error on playing Audio: $error.errorCodeName}", error)

        val currentState = state.value
        val controller = currentState.getControllerOrNull()
        val item = currentState.getItemOrNull()
        val mediaItem = controller?.currentMediaItem

        if (controller != null && item != null && mediaItem != null) {
            if (mediaItemHelper.containsMediaItem(item, mediaItem)) {
                val newItem = mediaItemHelper.copyWithCurrentMediaItem(item, mediaItem)
                val newState = State.AudioError(controller, newItem, error)
                forceState(newState)
            } else {
                // FIXME: error and close player instead?
                log.warn("Android AudioPlayer has prepared another audio. Trigger reloading of requested articleAudio")
                enqueueAndPlay(item)
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

    /**
     * Called when a [MediaItem] is going to be played because of a skip/rewind action.
     * @param autoSkipped Must be true if the next item in the playlist was played automatically.
     */
    private fun onMediaItemSeek(nextMediaItem: MediaItem, autoSkipped: Boolean) {
        log.verbose("onMediaItemSeek($nextMediaItem, $autoSkipped)")

        val currentState = state.value
        val item = currentState.getItemOrNull()
        if (item == null) {
            log.warn("onMediaItemSeek(${nextMediaItem.toLogString()}) called but the player state has no item ${currentState.toLogString()}")
            return
        }

        when (item) {
            is ArticleAudio -> onMediaItemSeek(currentState, item, nextMediaItem)
            is IssueAudio -> onMediaItemSeek(currentState, item, nextMediaItem, autoSkipped)
            is PodcastAudio -> TODO()
        }
    }

    private fun onMediaItemSeek(currentState: State, item: ArticleAudio, nextMediaItem: MediaItem) {
        val isPreparingAudio = currentState is State.AudioPrepare || currentState is State.AudioQueued
        if (mediaItemHelper.containsMediaItem(item, nextMediaItem) && !isPreparingAudio) {
            // FIXME: error and close player instead?
            log.error("Seeking to a MediaItem(${nextMediaItem.toLogString()}, while the current item is $item. Reset playing the current item")
            enqueueAndPlay(item)
        }
    }

    private fun onMediaItemSeek(currentState: State, item: IssueAudio, nextMediaItem: MediaItem, autoSkipped: Boolean) {
        val nextIndex = item.indexOf(nextMediaItem)

        if (nextIndex < 0) {
            val isPreparingAudio =
                currentState is State.AudioPrepare || currentState is State.AudioQueued
            if (!isPreparingAudio) {
                log.error("Seeking to a MediaItem(${nextMediaItem.mediaId}, which is not found in $item. Reset playing the current item")
                enqueueAndPlay(item)
            }

        } else if (nextIndex == item.startIndex && autoSkipped) {
            // The playlist was looped one time (or the users skipped back and then reached the starting article again)
            val newItem = item.copy(currentIndex = nextIndex)
            val newState = currentState.copyWithItem(newItem)
            forceState(newState)
            currentState.getControllerOrNull()?.pause()

        } else if (nextIndex != item.currentIndex) {
            val newItem = item.copy(currentIndex = nextIndex)
            val newState = currentState.copyWithItem(newItem)
            forceState(newState)
        }
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

    private fun getControllerFromState(): MediaController? = state.value.getControllerOrNull()

    private fun State.getControllerOrNull(): MediaController? = when (this) {
        is State.AudioError -> controller
        is State.AudioPlaying -> controller
        is State.AudioPrepare -> controller
        is State.AudioReady -> controller
        is State.ControllerReady -> controller
        State.Init, is State.ControllerError, is State.AudioQueued -> null
    }

    private fun getItemFromState(): AudioPlayerItem? = state.value.getItemOrNull()

    private fun State.getItemOrNull(): AudioPlayerItem? = when (this) {
        is State.AudioError -> item
        is State.AudioPlaying -> item
        is State.AudioPrepare -> item
        is State.AudioQueued -> item
        is State.AudioReady -> item
        State.Init, is State.ControllerError, is State.ControllerReady -> null
    }

    private fun State.copyWithItem(newItem: AudioPlayerItem): State = when(this) {
        is State.AudioError -> copy(item = newItem)
        is State.AudioPlaying -> copy(item = newItem)
        is State.AudioPrepare -> copy(item = newItem)
        is State.AudioQueued -> copy(item = newItem)
        is State.AudioReady -> copy(item = newItem)
        State.Init, is State.ControllerError, is State.ControllerReady -> this
    }

    private fun State.toLogString(): String = when (this) {
        is State.AudioError -> "${this::class.simpleName}(${controller.hashCode()},$item)"
        is State.AudioPlaying -> "${this::class.simpleName}(${controller.hashCode()},$item)"
        is State.AudioPrepare -> "${this::class.simpleName}(${controller.hashCode()},$item)"
        is State.AudioQueued -> "${this::class.simpleName}($item)"
        is State.AudioReady -> "${this::class.simpleName}(${controller.hashCode()},$item)"
        is State.ControllerError -> "${this::class.simpleName}: ${this.exception}"
        is State.ControllerReady -> "${this::class.simpleName}(${controller.hashCode()})"
        State.Init -> "${this::class.simpleName}"
    }

    private fun MediaItem.toLogString(): String = "MediaItem($mediaId)"

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

    private fun trackAudioPlaying(item: AudioPlayerItem) {
        when (item) {
            is ArticleAudio ->
                tracker.trackAudioPlayerPlayArticleEvent(item.article)

            is IssueAudio ->
                tracker.trackAudioPlayerPlayArticleEvent(item.currentArticle)

            is PodcastAudio ->
                tracker.trackAudioPlayerPlayPodcastEvent(item.issueStub.issueKey, item.section, item.title)
        }
    }
    // endregion helper functions


    // region UiState
    private fun mapUiState(state: State, isExpanded: Boolean, playbackSpeed: Float, isAutoPlayNext: Boolean): UiState {
        return when (state) {
            State.Init -> UiState.Hidden
            is State.ControllerReady -> UiState.Hidden

            is State.ControllerError -> UiState.InitError(
                wasHandled = false,
                AudioPlayerException.Generic(cause = state.exception)
            )

            is State.AudioPrepare -> UiState.Initializing(uiStateHelper.asUiItem(state.item))
            is State.AudioQueued -> UiState.Initializing(uiStateHelper.asUiItem(state.item))
            is State.AudioReady -> UiState.Paused(
                UiState.PlayerState(
                    uiStateHelper.asUiItem(state.item),
                    isExpanded,
                    playbackSpeed,
                    isAutoPlayNext,
                    uiStateHelper.getUiStateControls(state.item, isAutoPlayNext),
                )
            )

            is State.AudioPlaying -> UiState.Playing(
                UiState.PlayerState(
                    uiStateHelper.asUiItem(state.item),
                    isExpanded,
                    playbackSpeed,
                    isAutoPlayNext,
                    uiStateHelper.getUiStateControls(state.item, isAutoPlayNext),
                )
            )

            is State.AudioError -> UiState.Error(
                wasHandled = false,
                UiState.PlayerState(
                    uiStateHelper.asUiItem(state.item),
                    isExpanded,
                    playbackSpeed,
                    isAutoPlayNext,
                    uiStateHelper.getUiStateControls(state.item, isAutoPlayNext),
                ),
                mapAudioErrorToException(state)
            )
        }
    }

    // endregion UiState
}
