package de.taz.app.android.audioPlayer

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.taz.app.android.DEFAULT_AUDIO_PLAYBACK_SPEED
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.AudioSpeaker
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.audioPlayer.MediaItemHelper.Companion.belongsTo
import de.taz.app.android.audioPlayer.MediaItemHelper.Companion.indexOfMediaItem
import de.taz.app.android.dataStore.AudioPlayerDataStore
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.PlaylistRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
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
 *
 *
 * What does it do:
 *  - it does hold the Playlist state (independent from player state aka when its playing)
 *  - it initializes and connects to the MediaService via a MediaController
 *  - it does sync between the ExoPlayer MediaItem list and the Playlist state
 *  - it plays the Disclaimer (if needed) at the end of the playlist
 *
 */
class AudioPlayerService private constructor(private val applicationContext: Context) :
    CoroutineScope {

    companion object : SingletonHolder<AudioPlayerService, Context>(::AudioPlayerService)

    private sealed class PlayerState {
        // Player/Controller is not initialized.
        // The player UI is hidden.
        data object Idle : PlayerState()

        // The Player/Controller is being started/connected.
        // The player UI might already be shown
        data class Connecting(val playWhenReady: Boolean) : PlayerState()

        // The Player/Controller is ready and a regular playlist MediaItem is queued on the Player
        // The player UI is shown.
        data class AudioReady(
            val controller: MediaController,
            val isPlaying: Boolean,
            val isLoading: Boolean,
        ) : PlayerState()

        // The Player/Controller is ready but the special Disclaimer MediaItem is queued on the Player
        // The player UI is shown.
        data class DisclaimerReady(
            val controller: MediaController,
            val isPlaying: Boolean,
            val isLoading: Boolean,
        ) : PlayerState()

        // The Player/Controller is ready, but the current MediaItem could not play due to an error
        // The player UI is shown.
        data class AudioError(
            val controller: MediaController,
            val exception: PlaybackException
        ) : PlayerState()
    }

    private val log by Log

    // Use the Main dispatcher as the default because all calls to the controller have to be from the Main thread
    private val coroutineJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineJob + Dispatchers.Main

    private val tracker = Tracker.getInstance(applicationContext)
    private val dataStore = AudioPlayerDataStore.getInstance(applicationContext)

    private val uiStateHelper = UiStateHelper(applicationContext)
    private val mediaItemHelper = MediaItemHelper(uiStateHelper)
    private val audioPlayerItemInitHelper =
        AudioPlayerItemInitHelper(applicationContext, uiStateHelper)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val playlistRepository = PlaylistRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)

    // Play the disclaimer only once per app session:
    private var disclaimerPlayed = false

    private var isPlaylistInitialized = false

    // Indication if the audio player is started for the very first time.
    // (Then it should start the Maxi-Player)
    private val isFirstAudioPlayFlow = dataStore.isFirstAudioPlayEver.asFlow().distinctUntilChanged()
    private var isFirstAudioPlay = true

    // Central internal state of the Service
    private val state: MutableStateFlow<PlayerState> = MutableStateFlow(PlayerState.Idle)

    // The Player state will not be synced on modifications of the playlistState.
    // But it is adapted when MediaItems change.
    // This allows us to set the playlistState and the MediaItems in one step without ending up in
    // an infinite circle.
    private val _audioQueueState: MutableStateFlow<Playlist> = MutableStateFlow(Playlist.EMPTY)
    private val _persistedPlaylistState: MutableStateFlow<Playlist> = MutableStateFlow(Playlist.EMPTY)
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Hidden)

    private val _errorEvents = MutableStateFlow<AudioPlayerErrorEvent?>(null)
    private val _playlistEvents = MutableStateFlow<AudioPlayerPlaylistEvent?>(null)

    private val _progress = MutableStateFlow<PlayerProgress?>(null)
    private var progressObserverJob: Job? = null

    private val playbackSpeedPreference = dataStore.playbackSpeed.asFlow().distinctUntilChanged()
    private var playbackSpeed: Float = DEFAULT_AUDIO_PLAYBACK_SPEED

    // Store the autoPlay preference as a StateFlow, so that we can access its value without a coroutine
    private val autoPlayNextPreference =
        dataStore.autoPlayNext.asFlow().stateIn(this, SharingStarted.Eagerly, false)

    private val initItemJob = SupervisorJob()
    private val initItemScope = CoroutineScope(coroutineContext + initItemJob)

    // region public attributes and methods
    val currentItem: Flow<AudioPlayerItem?> = _audioQueueState.map { it.getCurrentItem() }
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val persistedPlaylistState: StateFlow<Playlist> = _persistedPlaylistState.asStateFlow()
    val progress: StateFlow<PlayerProgress?> = _progress.asStateFlow()
    val errorEvents: StateFlow<AudioPlayerErrorEvent?> = _errorEvents.asStateFlow()
    val playlistEvents: StateFlow<AudioPlayerPlaylistEvent?> = _playlistEvents.asStateFlow()
    var isPlaylistPlayer = false
    var isIssuePlayer = false

    fun togglePlayIssue(issueStub: IssueStub) {
        if (isIssuePlayer && isPlaying()) {
            dismissPlayer()
        } else {
            playIssue(issueStub)
        }
    }

    private fun playIssue(issueStub: IssueStub) {
        showLoadingIfHidden()
        isIssuePlayer = true
        initItems {
            audioPlayerItemInitHelper.initIssueAudio(issueStub)
        }
    }

    fun enqueueArticle(articleKey: String) {
        initItems(enqueueInsteadOfPlay = true) {
            audioPlayerItemInitHelper.initArticleAudio(articleKey)
        }
    }

    fun playArticle(articleKey: String) {
        isIssuePlayer = false
        initItems(articleKey = articleKey) {
            audioPlayerItemInitHelper.initIssueOfArticleAudio(articleKey)
        }
    }

    fun playPlaylist(index: Int) {
        isIssuePlayer = false
        val newPlaylist = Playlist(index, _persistedPlaylistState.value.items)
        _persistedPlaylistState.compareAndSet(_persistedPlaylistState.value, newPlaylist)
        initItems {
            persistedPlaylistState.value.items
        }
    }

    fun playPodcast(issueStub: IssueStub, page: Page, audio: Audio) {
        isIssuePlayer = false
        initItems {
            audioPlayerItemInitHelper.initPagePodcast(issueStub, page, audio)
        }
    }

    fun playPodcast(issueStub: IssueStub, section: SectionOperations, audio: Audio) {
        isIssuePlayer = false
        initItems {
            audioPlayerItemInitHelper.initSectionPodcast(issueStub, section, audio)
        }
    }

    fun playSearchHit(searchHit: SearchHit) {
        isIssuePlayer = false
        initItems {
            audioPlayerItemInitHelper.initSearchHitAudio(searchHit)
        }
    }

    fun toggleAudioPlaying() {
        when (val state = state.value) {
            // Let the audio controller decide how to pause/play the current audio
            is PlayerState.AudioReady -> toggleAudioControllerPlaying(state.controller)
            is PlayerState.DisclaimerReady -> toggleAudioControllerPlaying(state.controller)
            is PlayerState.AudioError -> resumeAfterError(state.controller)
            is PlayerState.Connecting -> PlayerState.Connecting(!state.playWhenReady)

            // Try to re-prepare and play the audio
            PlayerState.Idle -> setCurrentAndPlay()
        }
    }

    /**
     * Maximize the player.
     * Replaces the Playlist and the MiniPlayer
     */
    fun maximizePlayer() {
        var updated = false
        while (!updated) {
            val currentUiState = _uiState.value
            val newUiState = when (currentUiState) {
                is UiState.MaxiPlayer -> currentUiState
                is UiState.MiniPlayer -> UiState.MaxiPlayer(currentUiState.playerState)
                // Ignore maximize requests when the player is not ready
                UiState.Hidden -> currentUiState
            }
            updated = _uiState.compareAndSet(currentUiState, newUiState)
        }
    }

    /**
     * Minimize the MaxiPlayer or the Playlist and show the MiniPlayer
     */
    fun minimizePlayer() {
        var updated = false
        while (!updated) {
            val currentUiState = _uiState.value
            val newUiState = when (currentUiState) {
                is UiState.MaxiPlayer -> UiState.MiniPlayer(currentUiState.playerState)
                is UiState.MiniPlayer -> currentUiState
                // Ignore minimize requests when the player is not ready
                UiState.Hidden -> currentUiState
            }
            updated = _uiState.compareAndSet(currentUiState, newUiState)
        }
    }


    private fun showLoadingIfHidden() {
        val currentUiState = _uiState.value
        if (currentUiState is UiState.Hidden) {
            connectController(playWhenReady = false)
            trySetStateIsLoading(true)
        }
    }

    /**
     * Return the next item from the playlist or null
     */
    fun getNextFromPlaylist(): AudioPlayerItem? = _audioQueueState.value.getNextItem()

    /**
     * Return the current item from the queue or null
     */
    fun getCurrent(): AudioPlayerItem? = _audioQueueState.value.getCurrentItem()

    /**
     * Dismiss the player and/or the playlist
     */
    fun dismissPlayer() {
        val controller = getControllerFromState()

        controller?.apply {
            stop()
            onControllerDismiss(this)
        }

        initItemJob.cancelChildren()
        forceState(PlayerState.Idle)
    }

    fun onErrorEventHandled(errorEvent: AudioPlayerErrorEvent) {
        _errorEvents.compareAndSet(errorEvent, null)
    }

    fun onPlaylistEventHandled(playlistEvent: AudioPlayerPlaylistEvent) {
        _playlistEvents.compareAndSet(playlistEvent, null)
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
        val breaks = _audioQueueState.value.getCurrentItem()?.audio?.breaks

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
        val breaks = _audioQueueState.value.getCurrentItem()?.audio?.breaks

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

    /**
     * Tries to remove a [playableKey] from the playlist.
     * Does nothing if the item does not exist.
     */
    fun removeItemFromPlaylist(playableKey: String) {
        val articleAsAudioItem =
            persistedPlaylistState.value.items.find { it.playableKey == playableKey }
        articleAsAudioItem?.let {
            removeItemFromPlaylist(it)
        }
    }

    /**
     * Tries to remove an [item] from the playlist.
     * Does nothing if the item does not exist.
     */
    fun removeItemFromPlaylist(item: AudioPlayerItem) {
        val itemIndex = _persistedPlaylistState.value.items.indexOf(item)
        val currentPlaylist = _persistedPlaylistState.value
        val items = currentPlaylist.items.toMutableList()

        if (itemIndex < 0) {
            log.warn("removeItem at position $itemIndex failed: not found in playlist")
            return
        }

        // Check if current is playing, dismiss the player if so
        if (isPlaying() && getCurrent() == item){
            dismissPlayer()
        }

        items.removeAt(itemIndex)
        val newCurrentIdx = if (itemIndex < currentPlaylist.currentItemIdx) {
            currentPlaylist.currentItemIdx-1
        } else {
            currentPlaylist.currentItemIdx
        }
        _persistedPlaylistState.value = Playlist(newCurrentIdx, items)
        _playlistEvents.value = AudioPlayerPlaylistRemovedEvent

        getControllerFromState()?.apply {
            val itemInPlaylist = getMediaItemAt(itemIndex)
            if (!itemInPlaylist.belongsTo(item)) {
                // FIXME: we have a mismatch between the controller playlist and our playlist
                log.error("mismatch between controller playlist and our playlist")
            }
            removeMediaItem(itemIndex)
        }
    }

    /**
     * Tries to clear the playlist
     */
    fun clearPlaylist() {
        val currentPlaylist = _persistedPlaylistState.value
        val items = currentPlaylist.items.toMutableList()

        if (items.isEmpty()) {
            log.warn("Current playlist is already empty")
            return
        }
        tracker.trackPlaylistClearedEvent()
        _persistedPlaylistState.value = Playlist(currentItemIdx = -1, items = emptyList())

        getControllerFromState()?.apply {
            if (isPlaylistPlayer) {
                clearMediaItems()
                dismissPlayer()
            }
        }
    }


    /**
     * Move an item in the playlist from [fromIndex] to [toIndex]
     * we do not check whether an item exists
     */
    fun moveItemInPlaylist(fromIndex: Int, toIndex: Int) {
        // do nothing if index is the same
        if (fromIndex == toIndex) {
            return
        }

        val currentPlaylist = _persistedPlaylistState.value
        val playlistLength = currentPlaylist.items.size

        // index out of bounds will be ignored
        if (fromIndex !in 0..<playlistLength || toIndex !in 0..<playlistLength) {
            log.warn("trying to swap items which are out of bounds")
            return
        }

        // move item in list
        val items = currentPlaylist.items.toMutableList()
        val itemInPlaylist = items.removeAt(fromIndex)
        items.add(toIndex, itemInPlaylist)

        // get new playing item
        var currentIndex = currentPlaylist.currentItemIdx
        val currentItemIdx = if (currentIndex == fromIndex) {
            toIndex
        } else {
            // if the item moved update index
            if (fromIndex > currentIndex && toIndex <= currentIndex) {
                currentIndex + 1
            } else if (fromIndex < currentIndex && toIndex >= currentIndex) {
                currentIndex - 1
            } else // item did not move
                currentIndex
        }
        getControllerFromState()?.apply {
            moveMediaItem(fromIndex, toIndex)
        }

        _persistedPlaylistState.value = Playlist(currentItemIdx, items)
        _audioQueueState.value = _persistedPlaylistState.value
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

    suspend fun setPlaybackSpeed(playbackSpeed: Float) {
        tracker.trackAudioPlayerChangePlaySpeedEvent(playbackSpeed)
        // Only set the playback speed on the dataStore - setting the playback speed on the controller
        // will be handled by an observer on the dataStore entry.
        dataStore.playbackSpeed.set(playbackSpeed)
    }

    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    fun isPlaying(): Boolean {
        return uiState.value.getPlayerStateOrNull() is UiState.PlayerState.Playing
    }

    fun isInPlaylistFlow(articleOperations: ArticleOperations): Flow<Boolean> {
        return persistedPlaylistState.map { playlistState ->
            playlistState.items.any { it.playableKey == articleOperations.key }
        }
    }
    fun isInPlaylistFlow(articleFileName: String): Flow<Boolean> {
        return persistedPlaylistState.map { playlistState ->
            playlistState.items.any { it.playableKey == articleFileName }
        }
    }
    // endregion public attributes and methods

    init {
        // Custom coroutine to map [State] to [UiState].
        // We can't use .map().stateIn() because we need a MutableStateFlow to be able to
        // get the current subscriber count and decide if we can release the [MediaController]
        launch {
            combine(
                state,
                _audioQueueState,
                playbackSpeedPreference,
                autoPlayNextPreference
            ) { state, playlist, playbackSpeed, autoPlayNext ->
                val currentUiState = _uiState.value
                mapUiState(state, playlist, currentUiState, playbackSpeed, autoPlayNext)
            }.collect {
                _uiState.value = it
            }
        }

        launch { // init playlist state
            _persistedPlaylistState.collect { newPlaylist ->
                // if newPlaylist is empty check to recover from repository:
                if (newPlaylist.isEmpty() && !isPlaylistInitialized) {
                    val recoveredPlaylist = playlistRepository.get()
                    _persistedPlaylistState.value = recoveredPlaylist
                }
                // save newPlaylist (if initialized):
                if (isPlaylistInitialized) {
                    playlistRepository.sync(newPlaylist)
                }
                isPlaylistInitialized = true
            }
        }

        launch { // init current play queue
            _audioQueueState.collect { playlist ->
                isPlaylistPlayer =
                    persistedPlaylistState.value.items == playlist.items && playlist.items.isNotEmpty()
            }
        }


        // Trigger tracking if a different article is played
        launch(Dispatchers.Default) {
            var lastItemTracked: AudioPlayerItem? = null
            combine(
                state.filter { it is PlayerState.AudioReady && it.isPlaying },
                currentItem.filterNotNull().distinctUntilChangedBy { it }
            ) { _, playlist -> playlist }.filter { item -> item != lastItemTracked }.collect {
                trackAudioPlaying(it)
                lastItemTracked = it
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

        launch {
            isFirstAudioPlayFlow.collect {
                isFirstAudioPlay = it
            }
        }
    }

    private fun initItems(
        enqueueInsteadOfPlay: Boolean = false,
        articleKey: String? = null,
        init: suspend () -> List<AudioPlayerItem>,
    ) {
        initItemScope.launch {
            try {
                // Initialize the new items
                val newItems = init()
                val playlist = _persistedPlaylistState.value

                if (enqueueInsteadOfPlay) {
                    // enqueue the items
                    val alreadyInPlayList =
                        newItems.any { it.playableKey in playlist.items.map { it.playableKey } }
                    if (alreadyInPlayList) {
                        _playlistEvents.value = AudioPlayerPlaylistAlreadyEnqueuedEvent
                        return@launch
                    } else {
                        // add to playlist:
                        tracker.trackPlaylistEnqueueEvent()
                        _persistedPlaylistState.value = playlist.append(newItems)
                        _playlistEvents.value = AudioPlayerPlaylistAddedEvent
                    }
                } else {
                    // if an [articleKey] is given, we use the index of it for the
                    // initialization of the audioQueue Playlist(index, items)
                    val indexOfArticle = articleKey?.let {
                        newItems.indexOfFirst { items -> items.playableKey == it }.coerceAtLeast(0)
                    } ?: 0

                    val index = if (isPlaylistPlayer) {
                        playlist.currentItemIdx
                    } else {
                        indexOfArticle
                    }

                    when (val currentState = state.value) {
                        is PlayerState.AudioReady,
                            // Handle AudioError just as paused.
                        is PlayerState.AudioError,
                            -> {
                            val controller = requireNotNull(getControllerFromState())

                            _audioQueueState.value = Playlist(index, newItems)

                            controller.apply {
                                addMediaItems(
                                    0,
                                    newItems.map {
                                        mediaItemHelper.getMediaItem(it)
                                    })
                                seekTo(index, 0L)

                                playWhenReady = true
                                prepare()
                            }
                        }

                        // Override the playlist (which is currently playing the disclaimer) and
                        is PlayerState.DisclaimerReady -> {
                            _audioQueueState.value = Playlist(index, newItems)

                            currentState.controller.apply {
                                setMediaItems(newItems.map {
                                    mediaItemHelper.getMediaItem(it)
                                })
                                seekTo(index, 0L)
                                playWhenReady = true
                                prepare()
                            }
                        }

                        PlayerState.Idle, is PlayerState.Connecting -> {
                            _audioQueueState.value = Playlist(index, newItems)

                            connectController(true)
                        }
                    }
                }
            } catch (e: Exception) {
                _playlistEvents.value = AudioPlayerPlaylistErrorEvent
            }
        }
    }


    private fun setCurrentAndPlay() {
        when (state.value) {
            is PlayerState.AudioReady, is PlayerState.DisclaimerReady, is PlayerState.AudioError -> {
                val controller = requireNotNull(getControllerFromState())
                val (currentIdx, items) = _audioQueueState.value

                controller.apply {
                    setMediaItems(items.map {
                        mediaItemHelper.getMediaItem(it)
                    })
                    seekTo(currentIdx, 0L)
                    playWhenReady = true
                    prepare()
                }
            }
            is PlayerState.Connecting -> forceState(PlayerState.Connecting(playWhenReady = true))
            PlayerState.Idle -> connectController(playWhenReady = true)
        }
    }

    private fun playDisclaimerAfterCurrent(controller: MediaController, currentItem: AudioPlayerItem) {
        val useMaleSpeaker = when (currentItem.audio.speaker) {
            AudioSpeaker.MACHINE_MALE -> true
            AudioSpeaker.MACHINE_FEMALE -> false
            AudioSpeaker.HUMAN, AudioSpeaker.PODCAST, AudioSpeaker.UNKNOWN ->
                error("playDisclaimerAfterCurrent must only be called for machine read texts")
        }
        val disclaimerMediaItem = mediaItemHelper.createDisclaimerMediaItem(useMaleSpeaker)

        forceState(
            PlayerState.DisclaimerReady(controller, isPlaying = true, isLoading = true)
        )

        controller.apply {
            setMediaItem(disclaimerMediaItem)
            playWhenReady = true
            prepare()
        }
        disclaimerPlayed = true
    }

    private fun connectController(playWhenReady: Boolean) {
        log.verbose("Connecting MediaController")
        launch {
            forceState(PlayerState.Connecting(playWhenReady))

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
                // FIXME: get string from resources, or don't pass message at all but let the consumer decide?
                _errorEvents.value = AudioPlayerFatalErrorEvent(
                    "Fatal",
                    AudioPlayerException.Initialization(cause = e)
                )
                dismissPlayer()
            }
        }
    }

    private fun onControllerReady(controller: MediaController) {
        log.verbose("onControllerReady (${controller.hashCode()})")
        controller.apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(controllerListener)
            setPlaybackSpeed(playbackSpeed)
        }

        when (val state = state.value) {
            is PlayerState.Connecting -> prepareCurrentPlaylist(controller, state.playWhenReady)

            // Unexpected: Just dismiss this edge case (Keep it simple)
            PlayerState.Idle, is PlayerState.AudioError, is PlayerState.AudioReady, is PlayerState.DisclaimerReady -> dismissPlayer() // FIXME dismiss with error
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

    private fun prepareCurrentPlaylist(controller: MediaController, playWhenReady: Boolean) {
        log.verbose("prepareCurrentPlaylist(...) playlist: ${_audioQueueState.value}")
        val (currentItemIdx, currentItems) = _audioQueueState.value

        forceState(
            PlayerState.AudioReady(
                controller,
                isPlaying = playWhenReady,
                isLoading = playWhenReady
            )
        )

        controller.apply {
            if (currentItems.isNotEmpty()) {
                setMediaItems(currentItems.map {
                    mediaItemHelper.getMediaItem(it)
                })
                seekTo(currentItemIdx, 0L)
            }
            setPlayWhenReady(playWhenReady)
            setAutoPlayNext(autoPlayNextPreference.value)
            prepare()
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
            this.prepare()
            when (playbackState) {
                Player.STATE_READY, Player.STATE_BUFFERING -> {
                    playWhenReady = !playWhenReady
                }

                // When we reached the end of the playlist, the player will stop.
                // When a play is requested we will just replay the last audio again.
                Player.STATE_ENDED -> {
                    seekToDefaultPosition()
                    playWhenReady = true
                }

                // When the player gave up its resource its not playing anything right now.
                // When a play is requested we will just replay the last audio again.
                Player.STATE_IDLE -> {
                    seekToDefaultPosition()
                    playWhenReady = true
                    prepare()
                }
            }
        }
    }

    private fun resumeAfterError(controller: MediaController) {
        forceState(PlayerState.AudioReady(controller, isPlaying = true, isLoading = true))
        controller.apply {
            playWhenReady = true
            prepare()
        }
    }

    private fun launchProgressObserver() {
        progressObserverJob?.cancel()
        progressObserverJob = launch {
            while (currentCoroutineContext().isActive) {
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
                Player.STATE_ENDED -> {
                    // Attention: might also be called if the MediaItems are cleared,
                    // aka getControllerFromState()?.mediaItemCount == 0
                    val mediaItemCount = getControllerFromState()?.mediaItemCount ?: 0
                    if (mediaItemCount > 0) {
                        onAudioEnded()
                    }
                }
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
                Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> onAudioEnded()

                else -> trySetStateIsPlaying(playWhenReady)
            }
        }


        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            @Player.MediaItemTransitionReason reason: Int
        ) {
            if (mediaItem == null) {
                // The playlist became empty
                // FIXME: in the old code only happened on dismissPlayer() - not sure about new code
                return
            }

            when (reason) {
                // A new [AudioPlayerItem] is being played on a new playlist
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> onPlaylistChanged(mediaItem)
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ->
                    onMediaItemSeek(mediaItem)
            }
        }
    }

    private fun onPlaylistChanged(currentMediaItem: MediaItem) {
        val currentState = state.value
        val controller = currentState.getControllerOrNull()
        val currentPlaylist = _audioQueueState.value

        if (controller == null) {
            return
        }

        // We have just enqueued the disclaimer - we don't have to do anything
        if (mediaItemHelper.isDisclaimer(currentMediaItem) && currentState is PlayerState.DisclaimerReady) {
            return
        }

        // Check if the Player MediaItem list seems to be the same as our PlaylistState
        val currentMediaItemIndex = currentPlaylist.items.indexOfMediaItem(currentMediaItem)

        if (currentMediaItemIndex < 0 || currentMediaItemIndex != controller.currentMediaItemIndex || currentPlaylist.items.size != controller.mediaItemCount) {
            log.error("Android AudioPlayers MediaItems are different from AudioPlayerService Playlist")
            dismissPlayer() // FIXME: add warning/toast
        }

        // Somehow the MediaItem changed. But as its part of our list we can simply adapt
        if (currentMediaItemIndex != currentPlaylist.currentItemIdx) {
            _audioQueueState.value = currentPlaylist.copy(currentItemIdx = currentMediaItemIndex)
            if (isPlaylistPlayer) {
                _persistedPlaylistState.value = _audioQueueState.value
            }
        }
    }

    private fun onAudioError(error: PlaybackException) {
        log.info("Error on playing Audio: $error.errorCodeName}", error)
        val controller = getControllerFromState()
        if (controller != null) {
            forceState(PlayerState.AudioError(controller, error))
        }
    }

    private fun onAudioEnded() {
        log.info("onAudioEnded()")
        val currentState = state.value
        val currentPlaylist = _audioQueueState.value
        val currentItem = currentPlaylist.getCurrentItem()
        val currentItemSpeakerIsMachine = when (currentItem?.audio?.speaker) {
            AudioSpeaker.MACHINE_MALE, AudioSpeaker.MACHINE_FEMALE -> true
            AudioSpeaker.HUMAN, AudioSpeaker.PODCAST, AudioSpeaker.UNKNOWN, null -> false
        }

        if (currentState is PlayerState.AudioReady && currentItem != null && currentItemSpeakerIsMachine && !disclaimerPlayed) {
            playDisclaimerAfterCurrent(currentState.controller, currentItem)
        } else {
            // Once the Audio has stopped, dismiss the player
            dismissPlayer()
        }
    }

    /**
     * Called when a [MediaItem] is going to be played because of a skip/rewind action.
     */
    private fun onMediaItemSeek(nextMediaItem: MediaItem) {
        log.verbose("onMediaItemSeek($nextMediaItem)")
        val currentPlaylist = _audioQueueState.value

        // Check if the Player MediaItem list seems to be the same as our PlaylistState
        val nextMediaItemIndex = currentPlaylist.items.indexOfMediaItem(nextMediaItem)
        if (nextMediaItemIndex < 0) {
            log.error("Android AudioPlayer seeks MediaItem not part of AudioPlayerService Playlist")
            dismissPlayer() // FIXME: add warning/toast
        }

        if (currentPlaylist.currentItemIdx != nextMediaItemIndex) {
            _audioQueueState.value = currentPlaylist.copy(currentItemIdx = nextMediaItemIndex)
            if (isPlaylistPlayer) {
                _persistedPlaylistState.value = _audioQueueState.value
            }
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
                is PlayerState.AudioReady -> currentState.copy(isLoading = isLoading)
                is PlayerState.DisclaimerReady -> currentState.copy(isLoading = isLoading)
                is PlayerState.AudioError, is PlayerState.Connecting, PlayerState.Idle ->
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
                is PlayerState.AudioReady -> currentState.copy(isPlaying = isPlaying)
                is PlayerState.DisclaimerReady -> currentState.copy(isPlaying = isPlaying)
                is PlayerState.AudioError, is PlayerState.Connecting, PlayerState.Idle ->
                    // Abort if the current state does not have a isPlaying property
                    return
            }
            updated = compareAndSetState(currentState, newState)
        }
    }

    // region helper functions
    /** See [MutableStateFlow.compareAndSet] */
    private fun compareAndSetState(expect: PlayerState, state: PlayerState): Boolean {
        val updated = this.state.compareAndSet(expect, state)
        if (updated) {
            log.verbose("compareAndSetState: SUCCESS\n\t${expect.toLogString()}\n\t${state.toLogString()}")
        } else {
            log.verbose("compareAndSetState: FAILED\n\t${expect.toLogString()}\n\t${state.toLogString()}\n\t${this.state.value.toLogString()}")
        }
        return updated
    }

    private fun forceState(state: PlayerState) {
        log.verbose("forceState\n\t${this.state.value.toLogString()}\n\t${state.toLogString()}")
        this.state.value = state
    }

    private fun getControllerFromState(): MediaController? = state.value.getControllerOrNull()

    private fun PlayerState.getControllerOrNull(): MediaController? = when (this) {
        is PlayerState.AudioError -> controller
        is PlayerState.AudioReady -> controller
        is PlayerState.DisclaimerReady -> controller
        PlayerState.Idle, is PlayerState.Connecting -> null
    }

    private fun PlayerState.toLogString(): String = when (this) {
        is PlayerState.AudioReady -> "${this::class.simpleName}(${controller.hashCode()}, isPlaying=$isPlaying, isLoading=$isLoading)"
        is PlayerState.DisclaimerReady -> "${this::class.simpleName}(${controller.hashCode()}, isPlaying=$isPlaying, isLoading=$isLoading)"
        is PlayerState.AudioError -> "${this::class.simpleName}(${controller.hashCode()}, ${this.exception.message})"
        PlayerState.Idle -> "${this::class.simpleName}"
        is PlayerState.Connecting -> "${this::class.simpleName}($playWhenReady)"
    }

    private fun MediaItem.toLogString(): String = "MediaItem($mediaId)"

    private fun mapAudioErrorToException(audioError: PlayerState.AudioError): AudioPlayerException {
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

    private suspend fun trackAudioPlaying(item: AudioPlayerItem) {
        when (item.type) {
            AudioPlayerItem.Type.ARTICLE -> {
                item.playableKey?.let { articleKey ->
                    articleRepository.getStub(articleKey)?.let { articleStub ->
                        tracker.trackAudioPlayerPlayArticleEvent(articleStub)
                    }
                }
            }
            AudioPlayerItem.Type.PODCAST ->
                tracker.trackAudioPlayerPlayPodcastEvent(item.audio.file.name)
            AudioPlayerItem.Type.SEARCH_HIT ->
                item.searchHit?.let { tracker.trackAudioPlayerPlaySearchHitEvent(it) }
            AudioPlayerItem.Type.DISCLAIMER -> {
                // Do not track when disclaimer is played
            }
        }
    }
    // endregion helper functions


    // region UiState
    private fun mapUiState(
        state: PlayerState,
        playlist: Playlist,
        uiState: UiState,
        playbackSpeed: Float,
        isAutoPlayNext: Boolean
    ): UiState {
        return when (state) {
            PlayerState.Idle -> {
                UiState.Hidden
            }

            is PlayerState.Connecting -> {
                uiState.copyWithPlayerState(
                    UiState.PlayerState.Initializing,
                    isFirstAudioPlay
                )
            }

            is PlayerState.AudioReady -> {
                val item: AudioPlayerItem? = playlist.getCurrentItem()
                val playerState = if (item == null) {
                    // this should never happen
                    UiState.PlayerState.Initializing
                } else {
                    val playerUiState = UiState.PlayerUiState(
                        item.uiItem,
                        playbackSpeed,
                        isAutoPlayNext,
                        uiStateHelper.getUiStateControls(playlist, isAutoPlayNext),
                        state.isLoading,
                    )

                    if (state.isPlaying) {
                        UiState.PlayerState.Playing(playerUiState)
                    } else {
                        UiState.PlayerState.Paused(playerUiState)
                    }
                }
                uiState.copyWithPlayerState(playerState, isFirstAudioPlay)
            }

            is PlayerState.AudioError -> {
                val item: AudioPlayerItem? = playlist.getCurrentItem()

                val audioPlayerException = mapAudioErrorToException(state)
                val errorMessage = if (audioPlayerException is AudioPlayerException.Network) {
                    applicationContext.getString(R.string.toast_no_connection_to_server)
                } else {
                    applicationContext.getString(R.string.toast_unknown_error)
                }
                _errorEvents.value = AudioPlayerInfoErrorEvent(errorMessage, audioPlayerException)

                val playerState = if (item == null) {
                    // this should never happen
                    UiState.PlayerState.Initializing
                } else {
                    UiState.PlayerState.Paused(
                        UiState.PlayerUiState(
                            item.uiItem,
                            playbackSpeed,
                            isAutoPlayNext,
                            uiStateHelper.getUiStateControls(playlist, isAutoPlayNext),
                            isLoading = true,
                        )
                    )
                }
                uiState.copyWithPlayerState(playerState)
            }


            is PlayerState.DisclaimerReady -> {
                val playerState = UiState.PlayerState.Playing(
                    UiState.PlayerUiState(
                        uiStateHelper.getDisclaimerUiItem(),
                        playbackSpeed,
                        isAutoPlayNext,
                        uiStateHelper.getDisclaimerUiStateControls(),
                        state.isLoading,
                    )
                )
                uiState.copyWithPlayerState(playerState)
            }
        }
    }

    // endregion UiState
}
