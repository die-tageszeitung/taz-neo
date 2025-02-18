package de.taz.app.android.audioPlayer

import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.TimeBar
import androidx.media3.ui.TimeBar.OnScrubListener
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.audioPlayer.DisplayMode.DISPLAY_MODE_MOBILE
import de.taz.app.android.audioPlayer.DisplayMode.DISPLAY_MODE_MOBILE_EXPANDED
import de.taz.app.android.audioPlayer.DisplayMode.DISPLAY_MODE_TABLET
import de.taz.app.android.audioPlayer.DisplayMode.DISPLAY_MODE_TABLET_EXPANDED
import de.taz.app.android.dataStore.AudioPlayerDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.AudioplayerOverlayBinding
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val CIRCULAR_PROGRESS_TICKS = 1000L
private val PLAYBACK_SPEEDS =
    floatArrayOf(0.5F, 0.7F, 0.8F, 0.9F, 1.0F, 1.1F, 1.2F, 1.3F, 1.5F, 2.0F)
private const val DELAYED_LOADING_STATE_MS = 500L

private enum class DisplayMode {
    DISPLAY_MODE_MOBILE,
    DISPLAY_MODE_MOBILE_EXPANDED,
    DISPLAY_MODE_TABLET,
    DISPLAY_MODE_TABLET_EXPANDED
}

/**
 * Controller to be attached to each activity that shall show the audio player overlay.
 * It adds the controller views as the last children of the activities root view and controls
 * its visibility and layout in response to the [UiState] emitted from  [AudioPlayerService].
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlayerViewController(
    private val activity: AppCompatActivity
) : CoroutineScope {

    init {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            onCreate()
        }

        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            onStart()
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = onCreate()
            override fun onStart(owner: LifecycleOwner) = onStart()
            override fun onStop(owner: LifecycleOwner) = onStop()
        })
    }
    private val log by Log

    private val coroutineJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineJob + Dispatchers.Main

    private lateinit var audioPlayerService: AudioPlayerService
    private lateinit var dataStore: AudioPlayerDataStore
    private lateinit var storageService: StorageService
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private lateinit var glideRequestManager: RequestManager
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var adapter: PlaylistAdapter

    // null, unless the player is already attached to the activities views
    private var playerOverlayBinding: AudioplayerOverlayBinding? = null
    private var boundUiItem: AudioPlayerItem.UiItem? = null
    private var isTabletMode: Boolean = false
    private var isLoading: Boolean = false

    private var delayedSetLoadingJob: Job? = null

    private fun onCreate() {
        audioPlayerService = AudioPlayerService.getInstance(activity.applicationContext)
        dataStore = AudioPlayerDataStore.getInstance(activity.applicationContext)
        storageService = StorageService.getInstance(activity.applicationContext)
        toastHelper = ToastHelper.getInstance(activity.applicationContext)
        tracker = Tracker.getInstance(activity.applicationContext)
        // FIXME (johannes): Re-consider adding the player views here if it prevents flickering during Activity changes
        isTabletMode = activity.resources.getBoolean(R.bool.isTablet)
        glideRequestManager = Glide.with(activity)
        generalDataStore = GeneralDataStore.getInstance(activity.applicationContext)
    }

    private fun onStart() {
        coroutineJob.cancelChildren()
        ensurePlayerOverlayIsAddedInFront()

        launch {
            audioPlayerService.uiState.collect {
                val binding = playerOverlayBinding
                if (binding != null) {
                    onPlayerUiChange(it, binding)
                }
            }
        }

        launch {
            audioPlayerService.progress.collect {
                if (!isLoading) {
                    updateProgressBars(it)
                }
            }
        }

        launch {
            audioPlayerService.errorEvents.filterNotNull().collect {
                handleErrorEvent(it)
            }
        }

        launch {
            audioPlayerService.playlistEvents.filterNotNull().collect{
                handlePlaylistEvent(it)
            }
        }
    }

    private fun onStop() {
        coroutineJob.cancelChildren()
        playerOverlayBinding?.hideOverlay()
    }

    private fun clearBoundData() {
        boundUiItem = null
    }

    private fun onPlayerUiChange(
        uiState: UiState,
        binding: AudioplayerOverlayBinding
    ) {
        disableBackHandling()

        when (uiState) {
            UiState.Hidden -> binding.apply {
                showLoadingState()
                hideOverlay()
                clearBoundData()
            }

            is UiState.MaxiPlayer -> {
                binding.enableCollapseOnTouchOutsideForMobile()
                enableBackHandling()
                showMaxiPlayer(uiState.playerState, binding)
            }

            is UiState.MiniPlayer -> {
                binding.disableCollapseOnTouchOutsideForMobile()
                showMiniPlayer(uiState.playerState, binding)
            }

            is UiState.Playlist -> {
                binding.enableCollapseOnTouchOutsideForMobile()
                enableBackHandling()
                showPlaylist(uiState.playlist, uiState.playerState, binding)
            }
        }
    }

    private fun showMaxiPlayer(
        playerState: UiState.PlayerState,
        binding: AudioplayerOverlayBinding
    ) {
        launch {
            dataStore.isFirstAudioPlayEver.set(false)
        }
        when (playerState) {
            UiState.PlayerState.Initializing -> {
                binding.showLoadingState()
            }

            is UiState.PlayerState.Paused ->
                binding.apply {
                    bindItem(playerState.playerUiState.uiItem)
                    setupLoadingState(playerState.playerUiState)
                    showExpandedPlayer(
                        isPlaying = false,
                        playerState.playerUiState.playbackSpeed,
                        playerState.playerUiState.isAutoPlayNext,
                        playerState.playerUiState.controls
                    )
                }

            is UiState.PlayerState.Playing -> binding.apply {
                bindItem(playerState.playerUiState.uiItem)
                setupLoadingState(playerState.playerUiState)
                showExpandedPlayer(
                    isPlaying = true,
                    playerState.playerUiState.playbackSpeed,
                    playerState.playerUiState.isAutoPlayNext,
                    playerState.playerUiState.controls
                )
            }
        }

    }

    private fun showMiniPlayer(
        playerState: UiState.PlayerState,
        binding: AudioplayerOverlayBinding
    ) {
        when (playerState) {
            // currently the loading state is only really possible with the expanded player.
            UiState.PlayerState.Initializing -> Unit

            is UiState.PlayerState.Paused -> binding.apply {
                bindItem(playerState.playerUiState.uiItem)
                setupLoadingState(playerState.playerUiState)
                showSmallPlayer(isPlaying = false)
            }

            is UiState.PlayerState.Playing -> binding.apply {
                bindItem(playerState.playerUiState.uiItem)
                setupLoadingState(playerState.playerUiState)
                showSmallPlayer(isPlaying = true)
            }
        }
    }

    private fun showPlaylist(
        playlist: Playlist,
        playerState: UiState.PlayerState,
        binding: AudioplayerOverlayBinding
    ) {
        when (playerState) {
            is UiState.PlayerState.Paused -> binding.apply {
                bindItem(playerState.playerUiState.uiItem)
                setupLoadingState(playerState.playerUiState)
                showPlaylist(playlist)
            }

            is UiState.PlayerState.Playing -> binding.apply {
                bindItem(playerState.playerUiState.uiItem)
                setupLoadingState(playerState.playerUiState)
                showPlaylist(playlist)
            }

            // FIXME: Add views/code to show the player init on the playlist
            UiState.PlayerState.Initializing -> binding.showPlaylist(playlist)
        }
    }

    private fun AudioplayerOverlayBinding.bindItem(uiItem: AudioPlayerItem.UiItem) {
        if (boundUiItem != uiItem) {

            audioTitle.text = uiItem.title
            audioAuthor.text = uiItem.author ?: ""

            expandedAudioTitle.text = uiItem.title
            expandedAudioAuthor.apply {
                isVisible = uiItem.author != null
                text = if (uiItem.author.isNullOrBlank()) {
                    ""
                } else {
                    "von ${uiItem.author}"
                }
            }

            bindAudioImages(uiItem)
            setupOpenItemInteractionHandlers(uiItem.openItemSpec)
        }
        boundUiItem = uiItem
    }

    private fun AudioplayerOverlayBinding.bindAudioImages(uiItem: AudioPlayerItem.UiItem) {
        if (uiItem.coverImageUri != null) {
            audioImage.apply {
                isVisible = true
                glideRequestManager.clear(this)
                setImageURI(uiItem.coverImageUri)
            }
            expandedAudioImage.apply {
                isVisible = true
                glideRequestManager.clear(this)
                setImageURI(uiItem.coverImageUri)
            }
        } else if (uiItem.coverImageGlidePath != null) {
            // FIXME (johannes): on other places we use a custom signature. thus we have to re-create the image here even if it was caches before
            audioImage.apply {
                isVisible = true
                glideRequestManager
                    .load(uiItem.coverImageGlidePath)
                    .centerCrop()
                    .into(this)
            }

            expandedAudioImage.apply {
                isVisible = true
                glideRequestManager
                    .load(uiItem.coverImageGlidePath)
                    .fitCenter()
                    .into(this)
            }
        } else {
            audioImage.apply {
                isVisible = false
                glideRequestManager.clear(this)
            }
            expandedAudioImage.apply {
                isVisible = false
                glideRequestManager.clear(this)
            }
        }
    }

    private fun setupLoadingState(playerUiState: UiState.PlayerUiState) {
        if (isLoading != playerUiState.isLoading) {
            isLoading = playerUiState.isLoading

            if (playerUiState.isLoading) {
                // Delay showing the loading state, to prevent some flickering when skipping to the next audio
                launchDelayedSetLoadingJob()

            } else {
                delayedSetLoadingJob?.cancel()
                delayedSetLoadingJob = null

                playerOverlayBinding?.apply {
                    audioProgress.isIndeterminate = false
                    expandedProgress.isVisible = true
                    expandedProgressLoadingOverlay.isVisible = false
                    // FIXME: maybe add handling of preview player of playlist here
                }
            }
        }
    }

    private fun launchDelayedSetLoadingJob() {
        // Only launch a new setLoading job if there is none still in the queue
        if (delayedSetLoadingJob?.isActive != true) {
            delayedSetLoadingJob = launch {
                delay(DELAYED_LOADING_STATE_MS)
                // Check if we are still in a loading state, otherwise ignore
                if (isLoading) {
                    playerOverlayBinding?.apply {
                        audioProgress.isIndeterminate = true
                        // Keep the actual TimeBar invisible for easier position of other elements
                        expandedProgress.isInvisible = true
                        expandedProgressLoadingOverlay.isVisible = true
                    }
                }
            }
        }
    }

    private fun updateProgressBars(progress: PlayerProgress?) {
        playerOverlayBinding?.apply {
            if (progress != null) {
                audioProgress.progress = if (progress.totalMs > 0L) {
                    (CIRCULAR_PROGRESS_TICKS * progress.currentMs / progress.totalMs).toInt()
                } else {
                    0
                }

                expandedProgress.apply {
                    setPosition(progress.currentMs)
                    setDuration(progress.totalMs)
                }

                val currentTime = DateHelper.millisecondsToMinuteString(progress.currentMs)
                val remainingTime =
                    DateHelper.millisecondsToMinuteString(progress.totalMs - progress.currentMs)
                expandedProgressCurrentTime.text = currentTime
                expandedProgressRemainingTime.text = "-$remainingTime"

            } else {
                audioProgress.progress = 0
                expandedProgress.apply {
                    setPosition(0L)
                    setDuration(0L)
                }
            }
        }
    }

    private fun AudioplayerOverlayBinding.showOverlay() {
        root.isVisible = true
    }

    private fun AudioplayerOverlayBinding.hideOverlay() {
        root.isVisible = false
    }

    private fun AudioplayerOverlayBinding.showLoadingState() {
        positionPlayerViews(isExpanded = true)
        showOverlay()
        smallPlayer.isVisible = false
        expandedPlayer.isVisible = true
        playlistView.isVisible = false
        setExpandedPlayerViewVisibility(isLoading = true)
    }

    private fun AudioplayerOverlayBinding.showSmallPlayer(isPlaying: Boolean) {
        positionPlayerViews(isExpanded = false)
        showOverlay()
        smallPlayer.isVisible = true
        expandedPlayer.isVisible = false
        playlistView.isVisible = false
        setSmallPlayerViewVisibility(isLoading = false)

        val imageResourceId: Int
        val imageLeftMarginPx: Int
        if (isPlaying) {
            imageResourceId = R.drawable.ic_pause_outline
            imageLeftMarginPx = 0
        } else {
            imageResourceId = R.drawable.ic_play_outline
            imageLeftMarginPx = activity.resources
                .getDimension(R.dimen.audioplayer_small_play_icon_left_offset)
                .toInt()
        }
        audioActionButtonImage.apply {
            setImageResource(imageResourceId)
            updateLayoutParams<FrameLayout.LayoutParams> {
                leftMargin = imageLeftMarginPx
            }
        }
    }

    private fun AudioplayerOverlayBinding.setSmallPlayerViewVisibility(isLoading: Boolean) {
        val isShowingPlayer = !isLoading
        audioImage.isVisible = isShowingPlayer && boundUiItem?.hasCoverImage == true
        audioTitle.isVisible = isShowingPlayer
        audioAuthor.isVisible = isShowingPlayer
        audioActionButton.isVisible = isShowingPlayer

        loadingMessage.isVisible = isLoading

        if (isShowingPlayer) {
            smallPlayer.setOnClickListener {
                audioPlayerService.maximizePlayer()
            }
        } else {
            smallPlayer.setOnClickListener(null)
        }
    }

    private fun AudioplayerOverlayBinding.showExpandedPlayer(
        isPlaying: Boolean,
        playbackSpeed: Float,
        isAutoPlayNext: Boolean,
        controls: UiState.Controls
    ) {
        positionPlayerViews(isExpanded = true)
        showOverlay()
        smallPlayer.isVisible = false
        expandedPlayer.isVisible = true
        playlistView.isVisible = false
        if (audioPlayerService.isPlaylistPlayer) {
            playlistControls.isVisible = true
            autoPlayLayout.isVisible = false
        } else {
            playlistControls.isVisible = false
            autoPlayLayout.isVisible = true
        }
        setExpandedPlayerViewVisibility(isLoading = false)

        val imageResourceId = if (isPlaying) {
            R.drawable.ic_pause_outline
        } else {
            R.drawable.ic_play_outline
        }
        expandedAudioAction.setImageResource(imageResourceId)
        expandedPlaybackSpeed.apply {
            val playbackSpeedString = playbackSpeed.toString().removeSuffix("0").removeSuffix(".")
            text = resources.getString(R.string.audioplayer_playback_speed, playbackSpeedString)
        }

        expandedSkipNextAction.apply {
            isInvisible =
                (controls.skipNext == UiState.ControlValue.HIDDEN || controls.skipNext == UiState.ControlValue.DISABLED)
            // FIXME (johannes): get image resources for disabled
            // val imageResourceId = if (controls.skipNext == UiState.ControlValue.DISABLED) {} else {}
            // setImageResource(imageResourceId)
        }

        expandedSkipPreviousAction.apply {
            isInvisible =
                (controls.skipPrevious == UiState.ControlValue.HIDDEN || controls.skipPrevious == UiState.ControlValue.DISABLED)
            // FIXME (johannes): get image resources for disabled
            // val imageResourceId = if (controls.skipPrevious == UiState.ControlValue.DISABLED) {} else {}
            // setImageResource(imageResourceId)
        }

        val next = audioPlayerService.getNextFromPlaylist()

        if (next != null) {
            expandedNextInQueue.text = next.uiItem.title
            expandedGoToPlaylist.isVisible = false
        } else {
            expandedNextInQueueTitle.isVisible = false
            expandedNextInQueue.isVisible = false
            expandedGoToPlaylist.apply {
                isVisible = true
                setOnClickListener {
                    audioPlayerService.showPlaylist()
                }
            }
        }

        if (controls.seekBreaks) {
            expandedForwardAction.setImageResource(R.drawable.ic_forward_break)
            expandedRewindAction.setImageResource(R.drawable.ic_backward_break)
        } else {
            expandedForwardAction.setImageResource(R.drawable.ic_forward_15)
            expandedRewindAction.setImageResource(R.drawable.ic_backward_15)
        }
    }

    private fun AudioplayerOverlayBinding.setExpandedPlayerViewVisibility(isLoading: Boolean) {
        val isShowingPlayer = !isLoading
        expandedAudioImage.isVisible = isShowingPlayer && boundUiItem?.hasCoverImage == true
        expandedAudioTitle.isVisible = isShowingPlayer
        expandedAudioAuthor.isVisible = isShowingPlayer
        expandedPlaybackSpeed.isVisible = isShowingPlayer
        expandedPlaybackSpeedTouchArea.isVisible = isShowingPlayer
        expandedProgress.isVisible = isShowingPlayer
        expandedProgressCurrentTime.isVisible = isShowingPlayer
        expandedProgressRemainingTime.isVisible = isShowingPlayer
        expandedSkipPreviousAction.isVisible = isShowingPlayer
        expandedRewindAction.isVisible = isShowingPlayer
        expandedAudioAction.isVisible = isShowingPlayer
        expandedForwardAction.isVisible = isShowingPlayer
        expandedSkipNextAction.isVisible = isShowingPlayer
        expandedNextInQueueTitle.isVisible = isShowingPlayer
        expandedNextInQueue.isVisible = isShowingPlayer
        expandedGoToPlaylist.isVisible = isShowingPlayer
        expandedPlaylistAction.isVisible = isShowingPlayer
        autoPlayLayout.isVisible = isShowingPlayer && !audioPlayerService.isPlaylistPlayer
        playlistControls.isVisible = isShowingPlayer && audioPlayerService.isPlaylistPlayer
        expandedLoadingMessage.isVisible = isLoading
    }

    private fun AudioplayerOverlayBinding.showPlaylist(playlistData: Playlist) {
        adapter.submitPlaylist(playlistData)

        val wasPlaylistVisible = playlistView.isVisible

        positionPlayerViews(isExpanded = true, fullScreen = true)
        showOverlay()
        smallPlayer.isVisible = false
        expandedPlayer.isVisible = false
        playlistView.isVisible = true
        playlistEmpty.isVisible = playlistData.items.isEmpty()

        // Ensure the current playing is always visible:
        if (playlistData.currentItemIdx != -1 && !wasPlaylistVisible) {
            playlistRv.smoothScrollToPosition(playlistData.currentItemIdx)
        }

        title.setOnClickListener {
            // Do nothing. Otherwise clicks behind the header are caught
        }
        deleteLayout.setOnClickListener {
            audioPlayerService.clearPlaylist()
        }
    }

    private fun ensurePlayerOverlayIsAddedInFront() {
        if (playerOverlayBinding == null) {
            val playerOverlayBinding = createPlayerOverlay()
            val rootView = activity.getRootView()
            playerOverlayBinding.hideOverlay()
            addPlayerOverlay(rootView, playerOverlayBinding)

            playerOverlayBinding.apply {
                positionPlayerViews(isExpanded = false)
                setupPlayerBackground()
                setupProgressBars()
                setupUserInteractionHandlers()
            }

            this.playerOverlayBinding = playerOverlayBinding

            val currentAdapter = playerOverlayBinding.playlistRv.adapter
            if (currentAdapter !is PlaylistAdapter) {
                adapter = PlaylistAdapter(audioPlayerService)
                playerOverlayBinding.playlistRv.adapter = adapter
            }
        }
        playerOverlayBinding?.bringToFront()
    }

    private fun createPlayerOverlay(): AudioplayerOverlayBinding {
        val layoutInflater = activity.layoutInflater
        return AudioplayerOverlayBinding.inflate(layoutInflater)
    }

    private fun addPlayerOverlay(rootView: FrameLayout, binding: AudioplayerOverlayBinding) {
        // Let the overlay take the whole screen, but position the player controls
        // FIXME (johannes): not working with animations yet
        val overlayLayoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        rootView.addView(
            binding.root,
            overlayLayoutParams
        )
    }

    private fun AudioplayerOverlayBinding.setupPlayerBackground() {
        if (isTabletMode) {
            expandedPlayer.setBackgroundResource(R.drawable.audioplayer_background_expanded_rounded)
            playlistView.setBackgroundResource(R.drawable.audioplayer_background_expanded_rounded)
        } else {
            expandedPlayer.setBackgroundResource(R.drawable.audioplayer_background_expanded_rounded_top)
            playlistView.setBackgroundResource(R.drawable.audioplayer_background_expanded_rounded_top)
        }
    }

    private fun AudioplayerOverlayBinding.positionPlayerViews(isExpanded: Boolean, fullScreen: Boolean = false) {
        val resources = activity.resources
        val newHeight =  if (fullScreen) { MATCH_PARENT } else { WRAP_CONTENT }
        when (getDisplayMode(isExpanded)) {
            DISPLAY_MODE_MOBILE -> {
                val bottomNavHeightPx = resources.getDimension(R.dimen.nav_bottom_height).toInt()
                val marginPx =
                    resources.getDimension(R.dimen.audioplayer_small_overlay_margin).toInt()


                audioPlayerOverlay.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = MATCH_PARENT
                    height = newHeight
                    gravity = Gravity.BOTTOM
                    bottomMargin = bottomNavHeightPx + marginPx
                    marginStart = marginPx
                    marginEnd = marginPx
                }
            }

            DISPLAY_MODE_MOBILE_EXPANDED -> {
                audioPlayerOverlay.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = MATCH_PARENT
                    height = newHeight
                    gravity = Gravity.BOTTOM
                    bottomMargin = 0
                    marginStart = 0
                    marginEnd = 0
                }
            }

            DISPLAY_MODE_TABLET, DISPLAY_MODE_TABLET_EXPANDED -> {
                val bottomNavHeightPx = resources.getDimension(R.dimen.nav_bottom_height).toInt()
                val marginPx =
                    resources.getDimension(R.dimen.audioplayer_tablet_overlay_margin).toInt()
                val playerWidth = resources.getDimension(R.dimen.audioplayer_tablet_width).toInt()

                audioPlayerOverlay.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = playerWidth
                    height = newHeight
                    gravity = Gravity.BOTTOM or Gravity.END
                    bottomMargin = bottomNavHeightPx + marginPx
                    marginStart = marginPx
                    marginEnd = marginPx
                }
            }
        }
    }

    private fun AudioplayerOverlayBinding.setupProgressBars() {
        audioProgress.apply {
            max = CIRCULAR_PROGRESS_TICKS.toInt()
            progress = 0
            isIndeterminate = false
        }

        expandedProgress.apply {
            // Expand the progress bar over its natural width, so that the start and end of the line
            // aligns to the start/end of the parent. Note that the scrubber will overlapp
            val scrubberSize =
                activity.resources.getDimension(R.dimen.audioplayer_expanded_progress_scrubber_size)
            val scrubberSizeOffset = (-scrubberSize / 2).toInt()
            updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = scrubberSizeOffset
                marginEnd = scrubberSizeOffset
            }
        }
    }

    private fun AudioplayerOverlayBinding.setupUserInteractionHandlers() {
        closeButton.setOnClickListener {
            tracker.trackAudioPlayerCloseEvent()
            audioPlayerService.dismissPlayer()
        }

        audioActionButton.setOnClickListener { toggleAudioPlaying() }

        expandedCloseButton.setOnClickListener { audioPlayerService.minimizePlayer() }

        expandedAudioAction.setOnClickListener { toggleAudioPlaying() }
        expandedForwardAction.setOnClickListener { audioPlayerService.seekForward() }
        expandedRewindAction.setOnClickListener { audioPlayerService.seekBackward() }

        expandedProgress.addListener(onScrubListener)

        expandedPlayer.setOnClickListener {
            // Catch all clicks on the overlay view to prevent overlaying items from being clicked
        }

        expandedPlaybackSpeedTouchArea.setOnClickListener {
            launch {
                togglePlaybackSpeed()
            }
        }

        expandedSkipPreviousAction.setOnClickListener {
            audioPlayerService.skipToPrevious()
        }

        expandedSkipNextAction.setOnClickListener {
            audioPlayerService.skipToNext()
        }

        if (!isTabletMode) {
            touchOutside.setOnClickListener {
                touchOutside.isVisible = false
                audioPlayerService.minimizePlayer()
            }
        }

        expandedPlaylistAction.setOnClickListener {
            audioPlayerService.showPlaylist()
        }

        playlistCloseButton.setOnClickListener {
            if (audioPlayerService.isPlaying()) {
                audioPlayerService.minimizePlayer()
            } else {
                audioPlayerService.dismissPlayer()
            }
        }
    }

    private fun AudioplayerOverlayBinding.setupOpenItemInteractionHandlers(openItemSpec: OpenItemSpec?) {
        if (openItemSpec != null) {
            expandedAudioTitle.setOnClickListener { openItem(openItemSpec) }
            expandedAudioAuthor.setOnClickListener { openItem(openItemSpec) }
            expandedAudioImage.setOnClickListener { openItem(openItemSpec) }
        } else {
            expandedAudioTitle.apply {
                isClickable = false
                setOnClickListener(null)
            }
            expandedAudioAuthor.apply {
                isClickable = false
                setOnClickListener(null)
            }
            expandedAudioImage.apply {
                isClickable = false
                setOnClickListener(null)
            }
        }
    }

    private val onScrubListener = object : OnScrubListener {
        override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit
        override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            if (!canceled) {
                audioPlayerService.seekTo(position)
            }
        }
    }

    private fun toggleAudioPlaying() {
        if (audioPlayerService.isPlaying()) {
            tracker.trackAudioPlayerPauseEvent()
        } else {
            tracker.trackAudioPlayerResumeEvent()
        }

        audioPlayerService.toggleAudioPlaying()
    }

    private fun openItem(openItemSpec: OpenItemSpec) {
        // Collapse the player only if we are in the mobile mode. Keep it open in tablet mode.
        if (!isTabletMode) {
            audioPlayerService.minimizePlayer()
        }
        when (openItemSpec) {
            is OpenItemSpec.OpenIssueItemSpec -> {
                launch {
                    val isPdfMode = generalDataStore.pdfMode.get()
                    val intent = if (isPdfMode) {
                        PdfPagerActivity.newIntent(
                            activity,
                            IssuePublicationWithPages(openItemSpec.issueKey),
                            openItemSpec.displayableKey
                        )
                    } else {
                        IssueViewerActivity.newIntent(
                            activity,
                            IssuePublication(openItemSpec.issueKey),
                            openItemSpec.displayableKey
                        )
                    }

                    if (activity is IssueViewerActivity || activity is PdfPagerActivity) {
                        activity.finish()
                    }
                    activity.startActivity(intent)
                }
            }
        }
    }

    /**
     * Toggle between the playback speeds in the order of [PLAYBACK_SPEEDS]
     * If a unspecified value is encountered the playback speed is returned to 1
     */
    private suspend fun togglePlaybackSpeed() {
        val currentSpeed = audioPlayerService.getPlaybackSpeed()
        // Find the closest index to the current playback speed
        val currentIndex = PLAYBACK_SPEEDS.indexOfFirst { it >= currentSpeed }
        val newSpeed = if (currentIndex >= 0) {
            val nextIndex = (currentIndex + 1) % PLAYBACK_SPEEDS.size
            PLAYBACK_SPEEDS[nextIndex]
        } else {
            1F
        }
        audioPlayerService.setPlaybackSpeed(newSpeed)
    }

    /**
     * Try to bring the player overlay to the front.
     * This might be necessary if some Fragment is added directly to the android.R.id.content view.
     */
    // FIXME (johannes): consider windows and the decor view again for adding the player
    fun bringOverlayToFront() {
        playerOverlayBinding?.bringToFront()
    }

    // region back handling
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            audioPlayerService.minimizePlayer()
        }
    }

    private fun enableBackHandling() {
        if (!isTabletMode && !onBackPressedCallback.isEnabled) {
            activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)
            onBackPressedCallback.isEnabled = true
        }
    }

    private fun disableBackHandling() {
        onBackPressedCallback.apply {
            remove()
            isEnabled = false
        }
    }

    /**
     * Explicitly handle a back action from  [AppCompatActivity.onBackPressed] if the Activity
     * uses some custom logic there. This function should be called first before any other handling.
     * Note that it is not required to call this function if no custom [AppCompatActivity.onBackPressed]
     * is used as [AudioPlayerViewController] registers its own [OnBackPressedCallback] which will be used
     * by default by [AppCompatActivity].
     */
    @Deprecated("Activity.OnBackPressed is deprecated. We should move all our back logic to use onBackPressedDispatchers")
    fun onBackPressed(): Boolean {
        return if (onBackPressedCallback.isEnabled) {
            onBackPressedCallback.handleOnBackPressed()
            true
        } else {
            false
        }
    }
    // endregion

    // region helpers
    private fun AppCompatActivity.getRootView(): FrameLayout {
        // FIXME: look at decor window logic from androids own Dialog views
        return findViewById(android.R.id.content)
    }

    // Optimized bring to front method
    private fun AudioplayerOverlayBinding.bringToFront() {
        val parent = root.parent
        if (parent is ViewGroup) {
            val lastViewIndex = parent.childCount - 1
            val lastView = parent.getChildAt(lastViewIndex)
            if (lastView != root) {
                root.parent.bringChildToFront(root)
            }
        } else {
            root.parent.bringChildToFront(root)
        }
    }

    private fun getDisplayMode(isExpanded: Boolean): DisplayMode = when {
        isTabletMode && isExpanded -> DISPLAY_MODE_TABLET_EXPANDED
        isTabletMode && !isExpanded -> DISPLAY_MODE_TABLET
        isExpanded -> DISPLAY_MODE_MOBILE_EXPANDED
        else -> DISPLAY_MODE_MOBILE
    }

    /*
     * On mobile show frame layout in background which minimizes the player on click:
     */
    private fun AudioplayerOverlayBinding.enableCollapseOnTouchOutsideForMobile() {
        if (!isTabletMode) touchOutside.isVisible = true
    }

    private fun AudioplayerOverlayBinding.disableCollapseOnTouchOutsideForMobile() {
        if (!isTabletMode) touchOutside.isVisible = false
    }
    // endregion helpers

    private fun handleErrorEvent(errorEvent: AudioPlayerErrorEvent) {
        // FIXME: add better handling. maybe add strings here
        when (errorEvent) {
            is AudioPlayerFatalErrorEvent -> toastHelper.showToast(errorEvent.message, long = true)
            is AudioPlayerInfoErrorEvent -> toastHelper.showToast(errorEvent.message, long = true)
        }
        audioPlayerService.onErrorEventHandled(errorEvent)
    }

    private fun handlePlaylistEvent(event: AudioPlayerPlaylistEvent) {
        val rootView = activity.window.decorView.rootView
        val anchorView =
            if (rootView.findViewById<BottomNavigationView>(R.id.navigation_bottom_webview_pager)?.isShown == true) {
                rootView.findViewById<BottomNavigationView>(R.id.navigation_bottom_webview_pager)
            } else if (rootView.findViewById<LinearLayout>(R.id.navigation_bottom_layout)?.isShown == true) {
                rootView.findViewById<LinearLayout>(R.id.navigation_bottom_layout)
            } else {
                null
            }
        when (event) {
            AudioPlayerPlaylistAddedEvent -> {
                SnackBarHelper.showPlayListSnack(
                    context = rootView.context,
                    view = rootView,
                    anchor = anchorView
                )
            }
            AudioPlayerPlaylistRemovedEvent -> {
                SnackBarHelper.showRemoveFromPlaylistSnack(
                    context = rootView.context,
                    view = rootView,
                    anchor = anchorView
                )
            }
            AudioPlayerPlaylistAlreadyEnqueuedEvent -> {
                SnackBarHelper.showAlreadyInPlaylistSnack(
                    context = rootView.context,
                    view = rootView,
                    anchor = anchorView
                )
            }
            AudioPlayerPlaylistErrorEvent -> toastHelper.showToast("Playlist Error", long = true)
        }
        audioPlayerService.onPlaylistEventHandled(event)
    }
}