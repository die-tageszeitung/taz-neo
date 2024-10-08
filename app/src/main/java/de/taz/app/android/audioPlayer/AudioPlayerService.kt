package de.taz.app.android.audioPlayer

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.MediaItemTransitionReason
import androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.taz.app.android.DEFAULT_AUDIO_PLAYBACK_SPEED
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.AudioSpeaker
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.Section
import de.taz.app.android.dataStore.AudioPlayerDataStore
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
        data class AudioInitQueued(val initItem: AudioPlayerItemInit): State()
        data class AudioQueued(val item: AudioPlayerItem) : State()

        data class AudioInit(
            val controller: MediaController,
            val initItem: AudioPlayerItemInit,
        ): State()

        data class AudioReady(
            val controller: MediaController,
            val item: AudioPlayerItem,
            val isPlaying: Boolean,
            val isLoading: Boolean,
        ) : State()

        data class DisclaimerReady(
            val controller: MediaController,
            val item: AudioPlayerItem,
            val isPlaying: Boolean,
            val isLoading: Boolean,
        ) : State()

        data class AudioError(
            val controller: MediaController,
            val item: AudioPlayerItem,
            val exception: PlaybackException,
        ) : State()

        data class AudioInitError(
            val controller: MediaController?,
            val exception: Exception,
        ) : State()
    }

    private val log by Log

    // Use the Main dispatcher as the default because all calls to the controller have to be from the Main thread
    private val coroutineJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineJob + Dispatchers.Main

    private val tracker = Tracker.getInstance(applicationContext)
    private val dataStore = AudioPlayerDataStore.getInstance(applicationContext)

    private val uiStateHelper = UiStateHelper(applicationContext)
    private val mediaItemHelper = MediaItemHelper(applicationContext, uiStateHelper)
    private val audioPlayerItemInitHelper = AudioPlayerItemInitHelper(applicationContext)

    // Play the disclaimer only once per app session:
    private var disclaimerPlayed = false

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

    private var initItemJob: Job? = null

    // region public attributes and methods
    val currentItem: Flow<AudioPlayerItem?> = state.map { it.getItemOrNull() }
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val progress: StateFlow<PlayerProgress?> = _progress.asStateFlow()

    fun playIssue(issueKey: AbstractIssueKey, articleStub: ArticleStub) {
        val issueOfArticleInit = IssueOfArticleInit(issueKey, articleStub)
        initItem(issueOfArticleInit)
    }

    fun playIssue(issueStub: IssueStub) {
        val issueInit = IssueInit(issueStub)
        initItem(issueInit)
    }

    fun playArticle(articleStub: ArticleStub) {
        val articleAudioInit = ArticleInit(articleStub)
        initItem(articleAudioInit)
    }

    fun playPodcast(issueStub: IssueStub, page: Page, audio: Audio) {
        val podcastAudio = PodcastAudio(
            issueStub,
            null,
            page,
            audio
        )
        enqueueAndPlay(podcastAudio)
    }

    fun playPodcast(issueStub: IssueStub, section: Section, audio: Audio) {
        val podcastAudio = PodcastAudio(
            issueStub,
            section,
            null,
            audio
        )
        enqueueAndPlay(podcastAudio)
    }

    fun toggleAudioPlaying() {
        when (val state = state.value) {
            // Try to re-prepare and play the audio
            is State.AudioError -> enqueueAndPlay(state.item)

            // Let the audio controller decide how to pause/play the current audio
            is State.AudioReady -> toggleAudioControllerPlaying(state.controller)
            is State.DisclaimerReady -> toggleAudioControllerPlaying(state.controller)

            // Ignore: no known audio is queued
            State.Init, is State.ControllerReady, is State.ControllerError, is State.AudioInit -> Unit

            // Ignore: the controller is not ready yet, so we can't easily control playing.
            // This is an edge case as the UI for pausing should not be shown without a controller
            // being ready.
            is State.AudioQueued, is State.AudioInitQueued, is State.AudioInitError-> Unit
        }
    }

    fun dismissPlayer() {
        val controller = getControllerFromState()

        controller?.apply {
            stop()
            onControllerDismiss(this)
        }

        initItemJob?.cancel()
        initItemJob = null

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
        val breaks = getItemFromState()?.audio?.breaks

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
        val breaks = getItemFromState()?.audio?.breaks

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
                    is State.AudioReady -> {
                        if (state.isPlaying && lastItemTracked != state.item) {
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

    private fun initItem(initItem: AudioPlayerItemInit) {
        var updated = false
        var prevState: State? = null

        while (!updated) {
            prevState = state.value
            val newState = when (prevState) {
                State.Init, is State.AudioError, is State.AudioInitQueued, is State.AudioQueued, is State.ControllerError ->
                    State.AudioInitQueued(initItem)
                is State.AudioInit -> State.AudioInit(prevState.controller, initItem)
                is State.AudioReady -> State.AudioInit(prevState.controller, initItem)
                is State.ControllerReady -> State.AudioInit(prevState.controller, initItem)
                is State.DisclaimerReady -> State.AudioInit(prevState.controller, initItem)
                is State.AudioInitError -> if (prevState.controller != null) {
                    State.AudioInit(requireNotNull(prevState.controller), initItem)
                } else {
                    State.AudioInitQueued(initItem)
                }
            }
            updated = compareAndSetState(prevState, newState)
        }

        if (prevState.controllerNeedsConnection()) {
            connectController()
        }

        initItemJob?.cancel()
        initItemJob = launch {
            try {
                val audioItem = audioPlayerItemInitHelper.initAudioPlayerItem(initItem)
                onAudioPlayerItemInit(initItem, audioItem)
            } catch (e: CancellationException) {
                log.verbose("AudioPlayer initialization was canceled")
            } catch (e: Exception) {
                onAudioPlayerItemError(initItem, e)
            }
        }
    }

    private fun onAudioPlayerItemInit(initItem: AudioPlayerItemInit, item: AudioPlayerItem) {
        val currentInitItem = when (val currentState = state.value) {
            is State.AudioInit -> currentState.initItem
            is State.AudioInitQueued -> currentState.initItem
            else -> null
        }
        if (currentInitItem == initItem) {
            enqueueAndPlay(item)
        }
    }

    private fun onAudioPlayerItemError(initItem: AudioPlayerItemInit, exception: Exception) {
        log.error("Could not initialize AudioPlayerItem for $initItem", exception)
        val currentInitItem = when (val currentState = state.value) {
            is State.AudioInit -> currentState.initItem
            is State.AudioInitQueued -> currentState.initItem
            else -> null
        }
        if (currentInitItem == initItem) {
            forceState(State.AudioInitError(getControllerFromState(), exception))
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
                is State.AudioError -> State.AudioReady(prevState.controller, item, isPlaying = true, isLoading = true)
                // Overwrite the current preparation
                is State.AudioReady -> State.AudioReady(prevState.controller, item, isPlaying = true, isLoading = true)
                is State.ControllerReady -> State.AudioReady(prevState.controller, item, isPlaying = true, isLoading = true)
                is State.DisclaimerReady -> State.AudioReady(prevState.controller, item, isPlaying = true, isLoading = true)
                is State.AudioInit -> State.AudioReady(prevState.controller, item, isPlaying = true, isLoading = true)
                // Overwrite the queued Audio
                is State.AudioQueued -> State.AudioQueued(item)
                is State.AudioInitQueued -> State.AudioQueued(item)
                is State.ControllerError, State.Init -> State.AudioQueued(item)
                is State.AudioInitError -> if (prevState.controller != null) {
                    State.AudioReady(requireNotNull(prevState.controller), item, isPlaying = true, isLoading = true)
                } else {
                    State.AudioQueued(item)
                }
            }
            updated = compareAndSetState(prevState, newState)
        }

        // Trigger operations required for the transition
        when {
            newState is State.AudioReady -> launch {
                prepareAudio(newState.controller, newState.item)
            }

            newState is State.AudioQueued && prevState.controllerNeedsConnection() ->
                connectController()

            else -> Unit
        }
    }

    private fun enqueueAndPlayDisclaimer(controller: MediaController, item: AudioPlayerItem) {
        val useMaleSpeaker = when (item.audio.speaker) {
            AudioSpeaker.MACHINE_MALE -> true
            AudioSpeaker.MACHINE_FEMALE -> false
            AudioSpeaker.HUMAN, AudioSpeaker.PODCAST, AudioSpeaker.UNKNOWN ->
                error("enqueueAndPlayDisclaimer must only be called for machine read texts")
        }
        val disclaimerMediaItem = mediaItemHelper.createDisclaimerMediaItem(useMaleSpeaker)

        forceState(
            State.DisclaimerReady(controller, item, isPlaying = true, isLoading = true)
        )

        controller.apply {
            setMediaItem(disclaimerMediaItem)
            repeatMode = REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
        disclaimerPlayed = true
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

        // FIXME(johannes): this should probably use compare and set, as the audio init might happen concurrently,
        //  but in reality the controller connection is quite fast and will probably not result in any problem
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
                forceState(State.AudioReady(controller, state.item, isPlaying = true, isLoading = true))
                prepareAudio(controller, state.item)
            }

            is State.AudioInitQueued -> {
                forceState(State.AudioInit(controller, state.initItem))
            }

            is State.AudioInitError -> {
                forceState(State.AudioInitError(controller, state.exception))
            }

            // illegal states
            is State.ControllerReady,
            is State.AudioError,
            is State.AudioReady,
            is State.DisclaimerReady,
            is State.AudioInit ->
                throw IllegalStateException("${state.toLogString()} not allowed onControllerReady")
        }

        launchProgressObserver()
    }

    private fun onControllerDismiss(controller: MediaController) {
        progressObserverJob?.cancel()
        controller.apply {
            removeListener(controllerListener)
            clearMediaItems()
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
            setMediaItems(mediaItems)
            repeatMode = REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    private suspend fun prepareIssueAudio(controller: MediaController, issueAudio: IssueAudio) {
        log.verbose("Preparing Issue Audio: $issueAudio")
        val mediaItems = mediaItemHelper.getMediaItems(issueAudio)
        controller.apply {
            setMediaItems(mediaItems, issueAudio.currentIndex, 0L)
            repeatMode = REPEAT_MODE_ALL
            setAutoPlayNext(autoPlayNextPreference.value)
            prepare()
            playWhenReady = true
        }
    }

    private suspend fun preparePodcastAudio(controller: MediaController, podcastAudio: PodcastAudio) {
        log.verbose("Preparing Podcast Audio: $podcastAudio")
        val mediaItems = mediaItemHelper.getMediaItems(podcastAudio)
        controller.apply {
            setMediaItems(mediaItems, 0, 0L)
            repeatMode = REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }


    @OptIn(UnstableApi::class)
    private fun MediaController.setAutoPlayNext(isAutoPlayNext: Boolean) {
        // While this is currently not using MediaController, we still keep it as an extension
        // function, as we want to be sure that the ArticleAudioMediaSessionService has already
        // initialized its mediaSession and the ExoPlayer within
        ArticleAudioMediaSessionService.exoPlayer?.pauseAtEndOfMediaItems = !isAutoPlayNext
    }

    private fun toggleAudioControllerPlaying(controller: MediaController) {
        controller.apply {
            when (playbackState) {
                Player.STATE_READY, Player.STATE_BUFFERING -> {
                    playWhenReady = !playWhenReady
                }

                // Ignore: the player should be hidden once the audio has ended. see onAudioEnded
                Player.STATE_ENDED -> Unit

                // Ignore: the play/pause button should not be shown while there is no audio prepared
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
        override fun onPlayerError(error: PlaybackException) {
            onAudioError(error)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> onAudioEnded()
                Player.STATE_IDLE -> trySetStateIsLoading(true)
                Player.STATE_BUFFERING -> trySetStateIsLoading(true)
                Player.STATE_READY -> trySetStateIsLoading(false)
            }
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            @Player.PlayWhenReadyChangeReason reason: Int
        ) {
            when (reason) {

                // When autoPlayNext is false, we instruct ExoPlayer.pauseAtEndOfMediaItems
                // Once the player pauses due to this we receive PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                // and end the audio player.
                PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> onAudioEnded()

                else -> trySetStateIsPlaying(playWhenReady)
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
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> onPlaylistChanged(mediaItem)
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> onMediaItemSeek(mediaItem, true)
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> onMediaItemSeek(mediaItem, false)
                // Ignored, as Android does not trigger it for REPEAT_MODE_ONE
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> Unit
            }
        }
    }

    private fun onPlaylistChanged(currentMediaItem: MediaItem) {
        val currentState = state.value
        val controller = currentState.getControllerOrNull()
        val item = currentState.getItemOrNull()

        if (controller != null && item != null) {
            if (mediaItemHelper.isDisclaimer(currentMediaItem) && currentState is State.DisclaimerReady) {
                // We have just enqueued the disclaimer - we don't have to do anything

            } else if (mediaItemHelper.containsMediaItem(item, currentMediaItem)) {
                val newItem = mediaItemHelper.copyWithCurrentMediaItem(item, currentMediaItem)
                val newState = currentState.copyWithItem(newItem)
                forceState(newState)

            } else {
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
        val currentState = state.value
        val currentItemSpeakerIsMachine = when (currentState.getItemOrNull()?.audio?.speaker) {
            AudioSpeaker.MACHINE_MALE, AudioSpeaker.MACHINE_FEMALE -> true
            AudioSpeaker.HUMAN, AudioSpeaker.PODCAST, AudioSpeaker.UNKNOWN, null -> false
        }

        if (currentState is State.AudioReady && currentItemSpeakerIsMachine && !disclaimerPlayed) {
            enqueueAndPlayDisclaimer(currentState.controller, currentState.item)
        } else {
            // Once the Audio has stopped, dismiss the player
            dismissPlayer()
        }
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
            is ArticleAudio -> onMediaItemSeekDefault(item, nextMediaItem)
            is IssueAudio -> onMediaItemSeekIssueAudio(currentState, item, nextMediaItem, autoSkipped)
            is PodcastAudio -> onMediaItemSeekDefault(item, nextMediaItem)
        }
    }

    private fun onMediaItemSeekDefault(item: AudioPlayerItem, nextMediaItem: MediaItem) {
        if (!mediaItemHelper.containsMediaItem(item, nextMediaItem)) {
            // FIXME: error and close player instead?
            log.error("Seeking to a MediaItem(${nextMediaItem.toLogString()}, while the current item is $item. Reset playing the current item")
            enqueueAndPlay(item)
        }
    }

    private fun onMediaItemSeekIssueAudio(currentState: State, item: IssueAudio, nextMediaItem: MediaItem, autoSkipped: Boolean) {
        val nextIndex = item.indexOf(nextMediaItem)

        if (nextIndex < 0) {
            // FIXME: error and close player instead?
            log.error("Seeking to a MediaItem(${nextMediaItem.mediaId}, which is not found in $item. Reset playing the current item")
            enqueueAndPlay(item)

        } else if (nextIndex == item.startIndex && autoSkipped) {
            // The playlist was looped one time (or the users skipped back and then reached the starting article again)
            val newItem = item.copy(currentIndex = nextIndex)
            val newState = currentState.copyWithItem(newItem)
            forceState(newState)
            currentState.getControllerOrNull()?.pause()

            // We just reached the end of the playlist
            onAudioEnded()

        } else if (nextIndex != item.currentIndex) {
            val newItem = item.copy(currentIndex = nextIndex)
            val newState = currentState.copyWithItem(newItem)
            forceState(newState)
        }
    }

    /**
     * Try to change the isLoading property of the current state.
     * If the current state does not have a isLoading property do nothing.
     */
    private fun trySetStateIsLoading(isLoading: Boolean) {
        var updated = false
        while (!updated) {
            val currentState = state.value
            val newState = when (currentState) {
                is State.AudioReady -> currentState.copy(isLoading = isLoading)
                is State.DisclaimerReady -> currentState.copy(isLoading = isLoading)
                is State.AudioError, is State.AudioQueued, is State.ControllerError, is State.ControllerReady, is State.AudioInitQueued, is State.AudioInit, State.Init, is State.AudioInitError ->
                    // Abort if the current state does not have a isLoading property
                    return
            }
            updated = compareAndSetState(currentState, newState)
        }
    }

    /**
     * Try to change the isPlaying property of the current state.
     * If the current state does not have a isPlaying property do nothing.
     */
    private fun trySetStateIsPlaying(isPlaying: Boolean) {
        var updated = false
        while (!updated) {
            val currentState = state.value
            val newState = when (currentState) {
                is State.AudioReady -> currentState.copy(isPlaying = isPlaying)
                is State.DisclaimerReady -> currentState.copy(isPlaying = isPlaying)
                is State.AudioError, is State.AudioQueued, is State.ControllerError, is State.ControllerReady, is State.AudioInitQueued, is State.AudioInit, State.Init, is State.AudioInitError ->
                    // Abort if the current state does not have a isPlaying property
                    return
            }
            updated = compareAndSetState(currentState, newState)
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
        is State.AudioReady -> controller
        is State.ControllerReady -> controller
        is State.DisclaimerReady -> controller
        is State.AudioInit -> controller
        is State.AudioInitError -> controller
        State.Init, is State.ControllerError, is State.AudioQueued, is State.AudioInitQueued -> null
    }

    private fun getItemFromState(): AudioPlayerItem? = state.value.getItemOrNull()

    private fun State.getItemOrNull(): AudioPlayerItem? = when (this) {
        is State.AudioError -> item
        is State.AudioQueued -> item
        is State.AudioReady -> item
        is State.DisclaimerReady -> item
        State.Init, is State.ControllerError, is State.ControllerReady, is State.AudioInitQueued, is State.AudioInit, is State.AudioInitError -> null
    }

    private fun State.copyWithItem(newItem: AudioPlayerItem): State = when(this) {
        is State.AudioError -> copy(item = newItem)
        is State.AudioQueued -> copy(item = newItem)
        is State.AudioReady -> copy(item = newItem)
        is State.DisclaimerReady -> copy(item = newItem)
        State.Init, is State.ControllerError, is State.ControllerReady, is State.AudioInitQueued, is State.AudioInit, is State.AudioInitError -> this
    }

    private fun State.toLogString(): String = when (this) {
        is State.AudioError -> "${this::class.simpleName}(${controller.hashCode()}, $item)"
        is State.AudioQueued -> "${this::class.simpleName}($item)"
        is State.AudioReady -> "${this::class.simpleName}(${controller.hashCode()}, $item, isPlaying=$isPlaying, isLoading=$isLoading)"
        is State.ControllerError -> "${this::class.simpleName}: ${this.exception}"
        is State.ControllerReady -> "${this::class.simpleName}(${controller.hashCode()})"
        is State.DisclaimerReady -> "${this::class.simpleName}(${controller.hashCode()}, $item, isPlaying=$isPlaying, isLoading=$isLoading)"
        is State.AudioInitQueued -> "${this::class.simpleName}($initItem)"
        is State.AudioInit -> "${this::class.simpleName}(${controller.hashCode()}, $initItem)"
        is State.AudioInitError -> "${this::class.simpleName}(${controller?.hashCode()}})"
        State.Init -> "${this::class.simpleName}"
    }

    /**
     * Returns true if the controller still needs a connection and [connectController] must be called.
     */
    private fun State?.controllerNeedsConnection(): Boolean =
        this is State.Init || this is State.ControllerError || (this is State.AudioInitError && this.controller == null)

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
                tracker.trackAudioPlayerPlayPodcastEvent(item.issueStub.issueKey, uiStateHelper.getTitleForPodcast(item))
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

            is State.AudioInitError -> UiState.InitError(
                wasHandled = false,
                AudioPlayerException.Generic(cause = state.exception)
            )

            is State.AudioInit -> UiState.Initializing
            is State.AudioInitQueued -> UiState.Initializing
            is State.AudioQueued -> UiState.Initializing

            is State.AudioReady ->
                if (state.isPlaying) {
                    UiState.Playing(
                        UiState.PlayerState(
                            uiStateHelper.asUiItem(state.item),
                            isExpanded,
                            playbackSpeed,
                            isAutoPlayNext,
                            uiStateHelper.getUiStateControls(state.item, isAutoPlayNext),
                            state.isLoading,
                        )
                    )
                } else {
                    UiState.Paused(
                        UiState.PlayerState(
                            uiStateHelper.asUiItem(state.item),
                            isExpanded,
                            playbackSpeed,
                            isAutoPlayNext,
                            uiStateHelper.getUiStateControls(state.item, isAutoPlayNext),
                            state.isLoading,
                        )
                    )
                }

            is State.AudioError -> UiState.Error(
                wasHandled = false,
                UiState.PlayerState(
                    uiStateHelper.asUiItem(state.item),
                    isExpanded,
                    playbackSpeed,
                    isAutoPlayNext,
                    uiStateHelper.getUiStateControls(state.item, isAutoPlayNext),
                    isLoading = true,
                ),
                mapAudioErrorToException(state)
            )

            is State.DisclaimerReady -> UiState.Playing(
                UiState.PlayerState(
                    uiStateHelper.getDisclaimerUiItem(),
                    isExpanded,
                    playbackSpeed,
                    isAutoPlayNext,
                    uiStateHelper.getDisclaimerUiStateControls(),
                    state.isLoading,
                )
            )
        }
    }

    // endregion UiState
}
