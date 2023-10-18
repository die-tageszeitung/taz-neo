package de.taz.app.android.audioPlayer

import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.TimeBar
import androidx.media3.ui.TimeBar.OnScrubListener
import de.taz.app.android.R
import de.taz.app.android.audioPlayer.DisplayMode.*
import de.taz.app.android.databinding.AudioplayerOverlayBinding
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val CIRCULAR_PROGRESS_TICKS = 1000L
private val PLAYBACK_SPEEDS = floatArrayOf(0.5F, 0.7F, 0.8F, 0.9F, 1.0F, 1.1F, 1.2F, 1.3F, 1.5F, 2.0F)

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

    private val coroutineJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = coroutineJob + Dispatchers.Main

    private lateinit var audioPlayerService: AudioPlayerService
    private lateinit var storageService: StorageService
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    // null, unless the player is already attached to the activities views
    private var playerOverlayBinding: AudioplayerOverlayBinding? = null
    private var boundItem: UiState.Item? = null
    private var isTabletMode: Boolean = false
    private var isPlaying: Boolean = false

    private fun onCreate() {
        audioPlayerService = AudioPlayerService.getInstance(activity.applicationContext)
        storageService = StorageService.getInstance(activity.applicationContext)
        toastHelper = ToastHelper.getInstance(activity.applicationContext)
        tracker = Tracker.getInstance(activity.applicationContext)
        // FIXME (johannes): Re-consider adding the player views here if it prevents flickering during Activity changes
        isTabletMode = activity.resources.getBoolean(R.bool.isTablet)
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
                updateProgressBars(it)
            }
        }
    }

    private fun onStop() {
        coroutineJob.cancelChildren()
        playerOverlayBinding?.hideOverlay()
    }

    private fun clearBoundData() {
        boundItem = null
    }

    private fun onPlayerUiChange(
        uiState: UiState,
        binding: AudioplayerOverlayBinding
    ) {
        disableBackHandling()
        if (uiState.isExpanded()) {
            enableBackHandling()
        }

        isPlaying = uiState is UiState.Playing

        when (uiState) {
            is UiState.Initializing -> binding.showLoadingState()
            UiState.Hidden -> binding.apply {
                showLoadingState()
                hideOverlay()
                clearBoundData()
            }

            is UiState.Paused -> binding.setupPreparedPlayer(
                false,
                uiState.playerState
            )

            is UiState.Playing -> binding.setupPreparedPlayer(
                true,
                uiState.playerState
            )

            is UiState.InitError -> {
                if (!uiState.wasHandled) {
                    toastHelper.showToast(R.string.audioplayer_error_generic, long = true)
                    audioPlayerService.onErrorHandled(uiState)
                }
            }

            is UiState.Error -> {
                binding.setupPreparedPlayer(false, uiState.playerState)
                if (!uiState.wasHandled) {
                    // FIXME (johannes): use custom popup
                    toastHelper.showToast(R.string.audioplayer_error_generic, long = true)
                    audioPlayerService.onErrorHandled(uiState)
                }
            }

        }
    }

    private fun AudioplayerOverlayBinding.setupPreparedPlayer(isPlaying: Boolean, playerState: UiState.PlayerState) {
        bindItem(playerState.item)
        if (playerState.expanded) {
            enableCollapseOnTouchOutsideForMobile()
            showExpandedPlayer(isPlaying, playerState.playbackSpeed, playerState.isAutoPlayNext, playerState.controls)

        } else {
            disableCollapseOnTouchOutsideForMobile()
            showSmallPlayer(isPlaying)
        }
    }

    private fun AudioplayerOverlayBinding.bindItem(item: UiState.Item) {
        if (boundItem != item) {

            audioTitle.text = item.title
            audioAuthor.text = item.author ?: ""

            audioImage.apply {
                isVisible = item.coverImageUri != null
                setImageURI(item.coverImageUri)
            }

            expandedAudioTitle.text = item.title
            expandedAudioAuthor.apply {
                isVisible = item.author != null
                text = item.author ?: ""
            }

            expandedAudioImage.apply {
                isVisible = item.coverImageUri != null
                setImageURI(item.coverImageUri)
            }
        }
        boundItem = item
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
        positionPlayerViews(isExpanded = false)
        showOverlay()
        smallPlayer.isVisible = true
        expandedPlayer.isVisible = false
        setSmallPlayerViewVisibility(isLoading = true)
    }

    private fun AudioplayerOverlayBinding.showSmallPlayer(isPlaying: Boolean) {
        positionPlayerViews(isExpanded = false)
        showOverlay()
        smallPlayer.isVisible = true
        expandedPlayer.isVisible = false
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
        audioImage.isVisible = isShowingPlayer && boundItem?.coverImageUri != null
        audioTitle.isVisible = isShowingPlayer
        audioAuthor.isVisible = isShowingPlayer
        audioActionButton.isVisible = isShowingPlayer

        loadingMessage.isVisible = isLoading

        if (isShowingPlayer) {
            smallPlayer.setOnClickListener {
                audioPlayerService.setPlayerExpanded(expanded = true)
            }
        } else {
            smallPlayer.setOnClickListener(null)
        }
    }

    private fun AudioplayerOverlayBinding.showExpandedPlayer(isPlaying: Boolean, playbackSpeed: Float, isAutoPlayNext: Boolean, controls: UiState.Controls) {
        positionPlayerViews(isExpanded = true)
        showOverlay()
        smallPlayer.isVisible = false
        expandedPlayer.isVisible = true

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
            isInvisible = (controls.skipNext == UiState.ControlValue.HIDDEN || controls.skipNext == UiState.ControlValue.DISABLED)
            // FIXME (johannes): get image resources for disabled
            // val imageResourceId = if (controls.skipNext == UiState.ControlValue.DISABLED) {} else {}
            // setImageResource(imageResourceId)
        }

        expandedSkipPreviousAction.apply {
            isInvisible = (controls.skipPrevious == UiState.ControlValue.HIDDEN || controls.skipPrevious == UiState.ControlValue.DISABLED)
            // FIXME (johannes): get image resources for disabled
            // val imageResourceId = if (controls.skipPrevious == UiState.ControlValue.DISABLED) {} else {}
            // setImageResource(imageResourceId)
        }

        expandedAutoPlayNextSwitch.apply {
            isGone = (controls.autoPlayNext == UiState.ControlValue.HIDDEN)
            isChecked = isAutoPlayNext
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
        } else {
            expandedPlayer.setBackgroundResource(R.drawable.audioplayer_background_expanded_rounded_top)
        }
    }

    private fun AudioplayerOverlayBinding.positionPlayerViews(isExpanded: Boolean) {
        val resources = activity.resources
        when (getDisplayMode(isExpanded)) {
            DISPLAY_MODE_MOBILE -> {
                val bottomNavHeightPx = resources.getDimension(R.dimen.nav_bottom_height).toInt()
                val marginPx =
                    resources.getDimension(R.dimen.audioplayer_small_overlay_margin).toInt()


                audioPlayerOverlay.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = MATCH_PARENT
                    height = WRAP_CONTENT
                    gravity = Gravity.BOTTOM
                    bottomMargin = bottomNavHeightPx + marginPx
                    marginStart = marginPx
                    marginEnd = marginPx
                }
            }

            DISPLAY_MODE_MOBILE_EXPANDED -> {
                audioPlayerOverlay.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = MATCH_PARENT
                    height = WRAP_CONTENT
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
                    height = WRAP_CONTENT
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

        expandedCloseButton.setOnClickListener { audioPlayerService.setPlayerExpanded(expanded = false) }

        expandedAudioAction.setOnClickListener { toggleAudioPlaying() }
        expandedForwardAction.setOnClickListener { audioPlayerService.seekForward() }
        expandedRewindAction.setOnClickListener { audioPlayerService.seekBackward() }

        expandedAudioTitle.setOnClickListener { openIssue() }
        expandedAudioAuthor.setOnClickListener { openIssue() }
        expandedAudioImage.setOnClickListener { openIssue() }

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

        expandedAutoPlayNextSwitch.setOnCheckedChangeListener { _, isChecked ->
            audioPlayerService.setAutoPlayNext(isChecked)
        }

        if (!isTabletMode) {
            touchOutside.setOnClickListener {
                touchOutside.isVisible = false
                audioPlayerService.setPlayerExpanded(expanded = false)
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
        if (isPlaying) {
            tracker.trackAudioPlayerPauseEvent()
        } else {
            tracker.trackAudioPlayerResumeEvent()
        }

        audioPlayerService.toggleAudioPlaying()
    }

    private fun openIssue() {
        // Collapse the player only if we are in the mobile mode. Keep it open in tablet mode.
        val expanded = isTabletMode
        audioPlayerService.setPlayerExpanded(expanded)
//        boundArticleIssueKey?.let { issueKey ->
//
//            val intent = IssueViewerActivity.newIntent(
//                activity,
//                IssuePublication(issueKey),
//                boundArticle?.key
//            )
//
//            if (activity is IssueViewerActivity) {
//                activity.finish()
//            }
//            activity.startActivity(intent)
//        }
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
            audioPlayerService.setPlayerExpanded(false)
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
}