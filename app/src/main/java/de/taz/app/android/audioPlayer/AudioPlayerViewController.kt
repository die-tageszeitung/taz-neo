package de.taz.app.android.audioPlayer

import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.TimeBar
import androidx.media3.ui.TimeBar.OnScrubListener
import de.taz.app.android.R
import de.taz.app.android.databinding.AudioPlayerOverlayBinding
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val CIRCULAR_PROGRESS_TICKS = 1000L

/**
 * Controller to be attached to each activity that shall show the audio player overlay.
 * It adds the controller views as the last children of the activities root view and controls
 * its visibility and layout in response to the [UiState] emitted from  [AudioPlayerService].
 *
 * FIXME: work in progress
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlayerViewController(
    private val activity: AppCompatActivity
) : CoroutineScope {

    private val log by Log

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

    // null, unless the player is already attached to the activities views
    private var playerOverlayBinding: AudioPlayerOverlayBinding? = null
    private var boundArticleAudio: ArticleAudio? = null
    private var boundArticleHasImage: Boolean = false

    private fun onCreate() {
        audioPlayerService = AudioPlayerService.getInstance(activity.applicationContext)
        storageService = StorageService.getInstance(activity.applicationContext)
        toastHelper = ToastHelper.getInstance(activity.applicationContext)
        // FIXME (johannes): Re-consider adding the player views here if it prevents flickering during Activity changes
    }

    private fun onStart() {
        log.verbose("onStart()")
        coroutineJob.cancelChildren()
        ensurePlayerOverlayIsAddedInFront()

        launch {
            audioPlayerService.uiState.collect {
                log.verbose("Collected: $it")
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
        log.verbose("onStop")
        coroutineJob.cancelChildren()
        playerOverlayBinding?.hideOverlay()
    }

    private fun onPlayerUiChange(
        uiState: UiState,
        binding: AudioPlayerOverlayBinding
    ) {
        when (uiState) {
            is UiState.Error -> {
                if (uiState.articleAudio != null) {
                    binding.bindArticleAudio(uiState.articleAudio)
                } else {
                    // show loading
                }
                binding.showOverlay()

                if (!uiState.wasHandled) {
                    // FIXME (johannes): use custom popup
                    toastHelper.showToast(R.string.audioplayer_error_generic, long = true)
                    audioPlayerService.onErrorHandled(uiState)
                }
            }

            UiState.Hidden -> binding.apply {
                showLoadingState()
                hideOverlay()
            }

            is UiState.Loading -> binding.showLoadingState()

            is UiState.Paused -> binding.apply {
                bindArticleAudio(uiState.articleAudio)
                if (uiState.expanded) {
                    showExpandedPlayer(isPlaying = false)
                } else {
                    showSmallPlayer(isPlaying = false)
                }
            }

            is UiState.Playing -> binding.apply {
                bindArticleAudio(uiState.articleAudio)
                if (uiState.expanded) {
                    showExpandedPlayer(isPlaying = true)
                } else {
                    showSmallPlayer(isPlaying = true)
                }
            }
        }
    }

    private fun AudioPlayerOverlayBinding.bindArticleAudio(articleAudio: ArticleAudio) {
        val article = articleAudio.article
        if (boundArticleAudio?.article?.key != articleAudio.article.key) {
            val articleTitle = article.title ?: article.key
            val authorText = article.authorList
                .mapNotNull { it.name }
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?.let {
                    activity.getString(
                        R.string.audioplayer_author_template,
                        it.joinToString(", ")
                    )
                }


            audioTitle.text = articleTitle
            audioAuthor.text = authorText ?: ""

            val articleImage = article.imageList.firstOrNull()
            val articleImageUriString = articleImage?.let { storageService.getFileUri(it) }
            val articleImageUri = articleImageUriString?.let { Uri.parse(it) }
            boundArticleHasImage = articleImageUri != null
            audioImage.apply {
                isVisible = boundArticleHasImage
                setImageURI(articleImageUri)
            }

            expandedAudioTitle.text = articleTitle
            expandedAudioAuthor.apply {
                isVisible = authorText != null
                text = authorText ?: ""
            }

            expandedAudioImage.apply {
                isVisible = articleImageUri != null
                setImageURI(articleImageUri)
            }


            boundArticleAudio = articleAudio
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

    private fun AudioPlayerOverlayBinding.showOverlay() {
        root.isVisible = true
    }

    private fun AudioPlayerOverlayBinding.hideOverlay() {
        root.isVisible = false
    }

    private fun AudioPlayerOverlayBinding.showLoadingState() {
        positionPlayerViews(isExpanded = false)
        showOverlay()
        smallPlayer.isVisible = true
        expandedPlayer.isVisible = false
        setSmallPlayerViewVisibility(isLoading = true)
    }

    private fun AudioPlayerOverlayBinding.showSmallPlayer(isPlaying: Boolean) {
        positionPlayerViews(isExpanded = false)
        showOverlay()
        smallPlayer.isVisible = true
        expandedPlayer.isVisible = false
        setSmallPlayerViewVisibility(isLoading = false)

        val imageResourceId = if (isPlaying) {
            R.drawable.ic_pause_outline
        } else {
            R.drawable.ic_play_outline
        }
        audioActionButtonImage.setImageResource(imageResourceId)
    }

    private fun AudioPlayerOverlayBinding.setSmallPlayerViewVisibility(isLoading: Boolean) {
        val isShowingPlayer = !isLoading
        audioImage.isVisible = isShowingPlayer && boundArticleHasImage
        audioTitle.isVisible = isShowingPlayer
        audioAuthor.isVisible = isShowingPlayer
        audioActionButton.isVisible = isShowingPlayer

        loadingMessage.isVisible = isLoading

        if (isShowingPlayer) {
            smallPlayer.setOnClickListener { audioPlayerService.setPlayerExpanded(expanded = true) }
        } else {
            smallPlayer.setOnClickListener(null)
        }
    }

    private fun AudioPlayerOverlayBinding.showExpandedPlayer(isPlaying: Boolean) {
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
    }

    private fun ensurePlayerOverlayIsAddedInFront() {
        if (playerOverlayBinding == null) {
            val playerOverlayBinding = createPlayerOverlay()
            val rootView = activity.getRootView()
            playerOverlayBinding.hideOverlay()
            addPlayerOverlay(rootView, playerOverlayBinding)
            playerOverlayBinding.positionPlayerViews(isExpanded = false)

            setupProgressBars(playerOverlayBinding)
            setupUserInteractionHandlers(playerOverlayBinding)

            this.playerOverlayBinding = playerOverlayBinding
        }
        playerOverlayBinding?.bringToFront()
    }

    private fun createPlayerOverlay(): AudioPlayerOverlayBinding {
        val layoutInflater = activity.layoutInflater
        return AudioPlayerOverlayBinding.inflate(layoutInflater)
    }

    private fun addPlayerOverlay(rootView: FrameLayout, binding: AudioPlayerOverlayBinding) {
        // Let the overlay take the whole screen, but position the player controls
        // FIXME (johannes): not working with animateions yet
        val overlayLayoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        rootView.addView(
            binding.root,
            overlayLayoutParams
        )
    }

    private fun AudioPlayerOverlayBinding.positionPlayerViews(isExpanded: Boolean) {
        if (isExpanded) {
            root.updateLayoutParams<FrameLayout.LayoutParams> {
                width = MATCH_PARENT
                height = WRAP_CONTENT
                gravity = Gravity.BOTTOM
                bottomMargin = 0
                marginStart = 0
                marginEnd = 0
            }
        } else {
            val bottomNavHeightPx = activity.resources.getDimension(R.dimen.nav_bottom_height).toInt()
            val marginPx = activity.resources.getDimension(R.dimen.audioplayer_small_overlay_margin).toInt()
            root.updateLayoutParams<FrameLayout.LayoutParams> {
                width = MATCH_PARENT
                height = WRAP_CONTENT
                gravity = Gravity.BOTTOM
                bottomMargin = bottomNavHeightPx + marginPx
                marginStart = marginPx
                marginEnd = marginPx
            }
        }

        // FIXME (johannes): i would prefer to animate the players themselves, and keep the framelayout unchanged,
        // but that doesnt work well - it shows animations when changing something within the controls
        // We Should probably add custom animations
        // smallPlayer.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        //     gravity = Gravity.BOTTOM
        //     bottomMargin = bottomNavHeightPx + marginPx
        //     marginStart = marginPx
        //     marginEnd = marginPx
        // }
        // ...
    }

    private fun setupProgressBars(binding: AudioPlayerOverlayBinding) {
        binding.audioProgress.apply {
            max = CIRCULAR_PROGRESS_TICKS.toInt()
            progress = 0
            isIndeterminate = false
        }

        binding.expandedProgress.apply {
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


    private fun setupUserInteractionHandlers(binding: AudioPlayerOverlayBinding) {
        binding.apply {
            closeButton.setOnClickListener { audioPlayerService.dismissPlayer() }

            audioActionButton.setOnClickListener { toggleAudioPlaying() }

            expandedCloseButton.setOnClickListener { audioPlayerService.setPlayerExpanded(expanded = false) }

            expandedAudioAction.setOnClickListener { toggleAudioPlaying() }
            expandedForwardAction.setOnClickListener { audioPlayerService.seekForward() }
            expandedRewindAction.setOnClickListener { audioPlayerService.seekBackward() }

            expandedAudioTitle.setOnClickListener { openIssue() }
            expandedAudioAuthor.setOnClickListener { openIssue() }
            expandedAudioImage.setOnClickListener { openIssue() }

            expandedProgress.addListener(onScrubListener)

            // FIXME: this is not working yet - probably needs a background
//            root.setOnClickListener {
//                // Catch all clicks on the overlay view to prevent overlayed items from being clicked
//            }
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
        audioPlayerService.toggleAudioPlaying()
    }

    private fun openIssue() {
        // FIXME: only set to non expanded if we are not in some kind of tablet mode
        audioPlayerService.setPlayerExpanded(expanded = false)
        boundArticleAudio?.let { currentArticleAudio ->

            val intent = IssueViewerActivity.newIntent(
                activity,
                IssuePublication(currentArticleAudio.issueKey),
                currentArticleAudio.article.key
            )

            if (activity is IssueViewerActivity) {
                activity.finish()
            }
            activity.startActivity(intent)
        }
    }

    // region helpers
    private fun AppCompatActivity.getRootView(): FrameLayout {
        // FIXME: look at decor window logic from androids own Dialog views
        return findViewById(android.R.id.content)
    }

    // Optimized bring to front method
    private fun AudioPlayerOverlayBinding.bringToFront() {
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
    // endregion helpers
}