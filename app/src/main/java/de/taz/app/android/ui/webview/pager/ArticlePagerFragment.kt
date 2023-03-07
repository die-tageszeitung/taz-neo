package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.util.Util
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

class ArticlePagerFragment : BaseMainFragment<FragmentWebviewPagerBinding>(), BackFragment {

    companion object {
        private const val ARTICLE_READER_AUDIO_BACK_SKIP_MS = 15000L
    }

    private val log by Log

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var toastHelper: ToastHelper

    private lateinit var articleBottomActionBarNavigationHelper: ArticleBottomActionBarNavigationHelper

    private var hasBeenSwiped = false
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()

    private var player: ExoPlayer? = null

    private var bottomBehavior: Behavior<View>? = null

    private var sectionDividerTransformer: SectionDividerTransformer? = null

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(requireContext().applicationContext)
                .setSeekBackIncrementMs(ARTICLE_READER_AUDIO_BACK_SKIP_MS)
                .build()
            viewBinding.playerController.player = player
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        viewBinding.playerController.player = null
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(requireContext().applicationContext)
        generalDataStore = GeneralDataStore.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireActivity().applicationContext)
    }

    override fun onResume() {
        super.onResume()
        issueContentViewModel.articleListLiveData.observeDistinct(this.viewLifecycleOwner) { articleStubsWithSectionKey ->
            if (
                articleStubsWithSectionKey.map { it.articleStub.key } !=
                (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.map { it.key }
            ) {
                log.debug("New set of articles: ${articleStubsWithSectionKey.map { it.articleStub.key }}")
                viewBinding.webviewPagerViewpager.adapter = ArticlePagerAdapter(articleStubsWithSectionKey, this)
                issueContentViewModel.displayableKeyLiveData.value?.let { tryScrollToArticle(it) }
            }
        }

        issueContentViewModel.displayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) {
            if (it != null) {
                tryScrollToArticle(it)
            }
        }


        issueContentViewModel.activeDisplayMode.observeDistinct(this) {
            // reset swiped flag on navigating away from article pager
            if (it != IssueContentDisplayMode.Article) {
                hasBeenSwiped = false
            }
        }
        issueContentViewModel.goNextArticle.observeDistinct(this) {
            if (it) {
                viewBinding.webviewPagerViewpager.currentItem = getCurrentPagerPosition() + 1
                issueContentViewModel.goNextArticle.value = false
            }
        }
        issueContentViewModel.goPreviousArticle.observeDistinct(this) {
            if (it) {
                viewBinding.webviewPagerViewpager.currentItem = getCurrentPagerPosition() - 1
                issueContentViewModel.goPreviousArticle.value = false
            }
        }
        if (Util.SDK_INT <= Build.VERSION_CODES.M) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= Build.VERSION_CODES.M) {
            stopMediaPlayer()
            releasePlayer()
            setArticleAudioMenuItem(isPlaying = false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleBottomActionBarNavigationHelper = ArticleBottomActionBarNavigationHelper(
            viewBinding.navigationBottom,
            onClickHandler = ::onBottomNavigationItemClicked
        )

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)

            (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
        }
        viewBinding.loadingScreen.root.visibility = View.GONE

        sectionDividerTransformer =
            SectionDividerTransformer(viewBinding.webviewPagerViewpager)
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
        // Android API level 24 (M) supports multiple windows.
        // So app can be visible but not active in split window mode.
        // Therefore we need to initialize the exo player in onStart (instead of onResume):
        // split window mode>
        if (Util.SDK_INT > Build.VERSION_CODES.M) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        // On Android with split screen support, the player needs to be released in onStop:
        if (Util.SDK_INT > Build.VERSION_CODES.M) {
            stopMediaPlayer()
            releasePlayer()
            setArticleAudioMenuItem(isPlaying = false)
        }
    }

    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked)
        }

        override fun onPageSelected(position: Int) {
            val nextStub =
                (viewBinding.webviewPagerViewpager.adapter as ArticlePagerAdapter).articleStubs[position]
            if (lastPage != null && lastPage != position) {
                // if position has been changed by 1 (swipe to left or right)
                if (abs(position - lastPage!!) == 1) {
                    hasBeenSwiped = true
                }
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                    nextStub
                ) { issueKey, displayable ->
                    log.debug("After swiping select displayable to ${displayable.key} (${displayable.title})")
                    if (issueContentViewModel.activeDisplayMode.value == IssueContentDisplayMode.Article) {
                        issueContentViewModel.setDisplayable(
                            IssueKeyWithDisplayableKey(
                                issueKey,
                                displayable.key
                            ),
                            immediate = true
                        )
                    }
                }
                // reset lastSectionKey as it might have changed the section by swiping
                if (hasBeenSwiped) {
                    issueContentViewModel.lastSectionKey = null
                    // in pdf mode update the corresponding page:
                    if (tag == ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) {
                        val pdfPageWhereArticleBegins = nextStub.pageNameList.firstOrNull()
                        pdfPageWhereArticleBegins?.let {
                            lifecycleScope.launch {
                                pdfPagerViewModel.goToPdfPage(it)
                            }
                        }
                    }
                }

                // if we are showing another article we will stop the player and hide the controls
                stopMediaPlayer()
                setArticleAudioMenuItem(isPlaying = false)
            }
            lastPage = position

            lifecycleScope.launchWhenResumed {
                // show the share icon always when in public issues (as it shows a popup that the user should log in)
                // OR when an onLink link is provided
                articleBottomActionBarNavigationHelper.setShareIconVisibility(
                    nextStub.onlineLink,
                    nextStub.key
                )

                isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                isBookmarkedLiveData = bookmarkRepository.createBookmarkStateFlow(nextStub.articleFileName).asLiveData()
                isBookmarkedLiveData?.observe(this@ArticlePagerFragment, isBookmarkedObserver)

                articleBottomActionBarNavigationHelper.setArticleAudioVisibility(nextStub.hasAudio)
            }
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            sectionDividerTransformer?.onPageScrolled(
                position, positionOffset, positionOffsetPixels
            )
        }
    }

    override fun onBackPressed(): Boolean {
        stopMediaPlayer()
        setArticleAudioMenuItem(isPlaying = false)

        // FIXME (johannes): please check about the usefulness of the following logic
        return if (hasBeenSwiped) {
            lifecycleScope.launch { showSectionOrGoBack() }
            true
        } else {
            return false
        }
    }

    private suspend fun showSectionOrGoBack(): Boolean {
        return getCurrentArticleStub()?.let { articleStub ->
            runIfNotNull(
                issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                articleStub.getSectionStub(requireContext().applicationContext)
            ) { issueKey, sectionStub ->
                issueContentViewModel.setDisplayable(
                    IssueKeyWithDisplayableKey(
                        issueKey,
                        sectionStub.key
                    )
                )
                true
            } ?: false
        } ?: false
    }

    private fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> MainActivity.start(requireActivity())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentArticleStub()?.let {
                    toggleBookmark(it)
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }

            R.id.bottom_navigation_action_audio -> onAudioAction()
        }
    }

    private fun toggleBookmark(articleStub: ArticleStub) {
        bookmarkRepository.toggleBookmarkAsync(articleStub.articleFileName)
    }

    fun share() {
        lifecycleScope.launch {
            getCurrentArticleStub()?.let { articleStub ->
                val url = articleStub.onlineLink
                url?.let {
                    shareArticle(url, articleStub.title)
                } ?: showSharingNotPossibleDialog()
            }
        }
    }

    private fun shareArticle(url: String, title: String?) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let {
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun tryScrollToArticle(articleKey: String) {
        val articleStubs =
            (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs
        if (
            articleKey.startsWith("art") &&
            articleStubs?.map { it.key }?.contains(articleKey) == true
        ) {
            if (articleKey != getCurrentArticleStub()?.key) {
                log.debug("I will now display $articleKey")
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                    }
                }
            }
            issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Article)
        }
    }

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position =
            (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.indexOfFirst {
                it.key == issueContentViewModel.displayableKeyLiveData.value
            }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentArticleStub(): ArticleStub? {
        return issueContentViewModel.articleListLiveData.value?.get(getCurrentPagerPosition())?.articleStub
    }

    private fun onAudioAction() {
        val articleStub = getCurrentArticleStub()
        if (articleStub == null) {
            return
        }

        if (articleStub.hasAudio) {
            if (player?.isPlaying == true) {
                stopMediaPlayer()
            } else {
                playAudioOfArticle()
            }
        }
    }

    private fun setArticleAudioMenuItem(isPlaying: Boolean) {
        articleBottomActionBarNavigationHelper.setArticleAudioMenuIcon(isPlaying)
    }

    private suspend fun getCurrentArticleAudioFile(): FileEntry? {
        return getCurrentArticleStub()?.articleFileName?.let { articleStub ->
            articleRepository.get(articleStub)
        }?.audioFile
    }

    private fun playAudioOfArticle() {
        fixToolbar()
        showPlayerController()
        viewBinding.playerController.findViewById<TextView>(R.id.title).apply {
            text = getCurrentArticleStub()?.title
        }
        if (getCurrentArticleStub()?.hasAudio == true) {
            lifecycleScope.launch {
                getCurrentArticleAudioFile()?.let { audioFile ->
                    // FIXME (johannes): i feel like we should use the StoragePathService.determinBaseUrl instead, but i am not sure yet how it works
                    val baseUrl =
                        getCurrentArticleStub()?.getIssueStub(requireContext().applicationContext)?.baseUrl
                    player?.also {
                        val mediaItem = MediaItem.fromUri("$baseUrl/${audioFile.name}")
                        it.setMediaItem(mediaItem)
                        it.prepare()
                        it.play()
                    }
                }
            }
            setArticleAudioMenuItem(isPlaying = true)
        }
    }

    private fun showPlayerController() {
        val navAndPlayerHeight =
            this.resources.getDimensionPixelSize(R.dimen.nav_bottom_height) + this.resources.getDimensionPixelSize(
                R.dimen.audio_player_bottom_height
            )
        viewBinding.playerController.visibility = View.VISIBLE
        viewBinding.webviewPagerViewpager.setPadding(
            0, 0, 0, navAndPlayerHeight
        )
    }

    private fun stopMediaPlayer() {
        releaseToolbar()
        hidePlayerController()
        player?.stop()
        setArticleAudioMenuItem(isPlaying = false)
    }

    private fun hidePlayerController() {
        viewBinding.playerController.visibility = View.GONE
        viewBinding.webviewPagerViewpager.setPadding(0, 0, 0, 0)
    }

    private fun fixToolbar() {
        val params =
            viewBinding.navigationBottomLayout.layoutParams as CoordinatorLayout.LayoutParams
        bottomBehavior = params.behavior
        params.behavior = null
    }

    private fun releaseToolbar() {
        // Restore the previous bottom behavior.
        // The variable bottomBehavior will be null if we never called fixToolbar,
        // or if the bottomBehavior was already null before.
        if (bottomBehavior != null) {
            val params =
                viewBinding.navigationBottomLayout.layoutParams as CoordinatorLayout.LayoutParams
            params.behavior = bottomBehavior
        }
    }

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        if (this.tag == ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) {
            // On device orientation changes the Fragments Activity is already destroyed when we reach the onDestroyView method.
            // Thus we cant initialize a ViewModel instance from onDestroyView.
            // As the `by activityViewModels()` is called lazily and not being used before, the ViewModel can not be initialized.
            // To prevent the app from crashing in this case we check explicitly that the Activity has not been destroyed yet.
            if (!this.requireActivity().isDestroyed)
                pdfPagerViewModel.hideDrawerLogo.postValue(true)
        }
        stopMediaPlayer()
        releasePlayer()
        setArticleAudioMenuItem(isPlaying = false)
        sectionDividerTransformer = null
        super.onDestroyView()
    }
}