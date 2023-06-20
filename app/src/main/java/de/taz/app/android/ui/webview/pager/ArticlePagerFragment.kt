package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.audioPlayer.AudioPlayerViewModel
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.getRecyclerView
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

class ArticlePagerFragment : BaseMainFragment<FragmentWebviewPagerBinding>(), BackFragment {

    private val log by Log

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private val audioPlayerViewModel: AudioPlayerViewModel by viewModels()

    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var toastHelper: ToastHelper

    private val articleBottomActionBarNavigationHelper =
        ArticleBottomActionBarNavigationHelper(::onBottomNavigationItemClicked)

    private var hasBeenSwiped = false
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()

    private var sectionDividerTransformer: SectionDividerTransformer? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(requireContext().applicationContext)
        generalDataStore = GeneralDataStore.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireActivity().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleBottomActionBarNavigationHelper
            .setBottomNavigationFromContainer(viewBinding.navigationBottomLayout)

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)

            (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
        }
        viewBinding.loadingScreen.root.visibility = View.GONE

        sectionDividerTransformer =
            SectionDividerTransformer(viewBinding.webviewPagerViewpager)

        issueContentViewModel.articleListLiveData.observe(viewLifecycleOwner) { articleStubsWithSectionKey ->
            if (
                articleStubsWithSectionKey.map { it.articleStub.key } !=
                (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.map { it.key }
            ) {
                viewBinding.webviewPagerViewpager.adapter = ArticlePagerAdapter(articleStubsWithSectionKey, this)
                issueContentViewModel.displayableKeyLiveData.value?.let { tryScrollToArticle(it) }
            }
        }

        issueContentViewModel.displayableKeyLiveData.observe(viewLifecycleOwner) {
            if (it != null) {
                tryScrollToArticle(it)
            }
        }


        issueContentViewModel.activeDisplayMode.distinctUntilChanged().observe(viewLifecycleOwner) {
            // reset swiped flag on navigating away from article pager
            if (it != IssueContentDisplayMode.Article) {
                hasBeenSwiped = false
            }
        }
        issueContentViewModel.goNextArticle.distinctUntilChanged().observe(viewLifecycleOwner) {
            if (it) {
                viewBinding.webviewPagerViewpager.currentItem = getCurrentPagerPosition() + 1
                issueContentViewModel.goNextArticle.value = false
            }
        }
        issueContentViewModel.goPreviousArticle.distinctUntilChanged().observe(viewLifecycleOwner) {
            if (it) {
                viewBinding.webviewPagerViewpager.currentItem = getCurrentPagerPosition() - 1
                issueContentViewModel.goPreviousArticle.value = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioPlayerViewModel.isActiveAudio.collect {
                        articleBottomActionBarNavigationHelper.setArticleAudioMenuIcon(it)
                    }
                }

                launch {
                    audioPlayerViewModel.isPlayerVisible.collect { isVisible ->
                        if (isVisible) {
                            articleBottomActionBarNavigationHelper.fixToolbar()
                        } else {
                            articleBottomActionBarNavigationHelper.releaseToolbar()
                        }
                    }
                }

                launch {
                    audioPlayerViewModel.errorMessageFlow.filterNotNull().collect { message ->
                        toastHelper.showToast(message, long = true)
                        audioPlayerViewModel.clearErrorMessage()
                    }
                }
            }
        }

    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
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
            }
            lastPage = position

            viewLifecycleOwner.lifecycleScope.launch {
                // show the share icon always when in public issues (as it shows a popup that the user should log in)
                // OR when an onLink link is provided
                articleBottomActionBarNavigationHelper.setShareIconVisibility(
                    nextStub.onlineLink,
                    nextStub.key
                )

                isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                isBookmarkedLiveData =
                    bookmarkRepository.createBookmarkStateFlow(nextStub.articleFileName)
                        .asLiveData()
                isBookmarkedLiveData?.observe(this@ArticlePagerFragment, isBookmarkedObserver)
            }

            audioPlayerViewModel.setIsVisibleArticle(nextStub)

            articleBottomActionBarNavigationHelper.apply {
                // show the player button only for articles with audio
                setArticleAudioVisibility(nextStub.hasAudio)
                // ensure the action bar is showing when the article changes
                expand(true)
            }

            // ensure the app bar of the webview is shown when article changes
            val articleWebViewFragment = viewBinding
                .webviewPagerViewpager
                .getRecyclerView()
                .layoutManager
                ?.findViewByPosition(position)
            val appBar = articleWebViewFragment?.findViewById<AppBarLayout>(R.id.app_bar_layout)
            val scrollView = articleWebViewFragment?.findViewById<NestedScrollView>(R.id.nested_scroll_view)

            if (appBar != null && scrollView != null) {
                // Scroll the webview content by the height of the appBar to prevent the content from jumping when paging. By default the appBar moves the top of the content below itself when it is set to expanded programmatically
                scrollView.scrollBy(0, appBar.height)
                appBar.setExpanded(true, false)
            }
            //FIXME(eike): Still a quirk when at bottom, the content is scrolled up again,
            // as the appBarLayout needs some space to expand
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
                getCurrentArticleStub()?.let { articleStub ->
                    if (articleStub.isImprint()) {
                        toastHelper.showToast(R.string.toast_imporint_not_possibile_to_bookmark)
                    } else {
                        toggleBookmark(articleStub)
                    }
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }

            R.id.bottom_navigation_action_audio -> audioPlayerViewModel.handleOnAudioActionOnVisibleArticle()
        }
    }

    private fun toggleBookmark(articleStub: ArticleStub) {
        lifecycleScope.launch {
            val isBookmarked = bookmarkRepository.toggleBookmarkAsync(articleStub.articleFileName).await()
            if (isBookmarked) {
                toastHelper.showToast(R.string.toast_article_bookmarked)
            }
            else {
                toastHelper.showToast(R.string.toast_article_debookmarked)
            }
        }
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

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        sectionDividerTransformer = null
        articleBottomActionBarNavigationHelper.onDestroyView()
        super.onDestroyView()
    }

}