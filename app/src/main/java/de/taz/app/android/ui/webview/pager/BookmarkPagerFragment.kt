package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.audioPlayer.ArticleAudioPlayerViewModel
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentWebviewArticlePagerBinding
import de.taz.app.android.monkey.pinToolbar
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsBottomSheetFragment
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.share.ShareArticleBottomSheet
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment.CollapsibleLayoutProvider
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BookmarkPagerFragment :
    BaseViewModelFragment<BookmarkPagerViewModel, FragmentWebviewArticlePagerBinding>(),
    CollapsibleLayoutProvider {

    val log by Log

    private lateinit var articlePagerAdapter: BookmarkPagerAdapter
    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var authHelper: AuthHelper
    private lateinit var tazApiCssDataStore: TazApiCssDataStore

    private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
        articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked)
    }
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    private val articleBottomActionBarNavigationHelper =
        ArticleBottomActionBarNavigationHelper(::onBottomNavigationItemClicked)


    override val viewModel: BookmarkPagerViewModel by activityViewModels()
    private val issueViewerViewModel: IssueViewerViewModel by activityViewModels()
    private val audioPlayerViewModel: ArticleAudioPlayerViewModel by viewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()

        articleBottomActionBarNavigationHelper
            .setBottomNavigationFromContainer(viewBinding.navigationBottomLayout)

        if (resources.getBoolean(R.bool.isTablet)) {
            articleBottomActionBarNavigationHelper.fixToolbarForever()
        }

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
        }


        viewModel.articleFileNameLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
            if (it != null) {
                setHeader(it)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bookmarkedArticleStubsFlow.collect {
                        articlePagerAdapter.articleStubs = it
                        viewBinding.loadingScreen.root.isVisible = false
                        tryScrollToArticle()
                    }
                }

                launch {
                    issueViewerViewModel.goNextArticle.collect {
                        if (it) {
                            viewBinding.webviewPagerViewpager.currentItem =
                                getCurrentPagerPosition() + 1
                            issueViewerViewModel.goNextArticle.value = false
                        }
                    }
                }

                launch {
                    issueViewerViewModel.goPreviousArticle.collect {
                        if (it) {
                            viewBinding.webviewPagerViewpager.currentItem =
                                getCurrentPagerPosition() - 1
                            issueViewerViewModel.goPreviousArticle.value = false
                        }
                    }
                }

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
                    audioPlayerViewModel.errorMessageFlow.filterNotNull()
                        .collect { message ->
                            toastHelper.showToast(message, long = true)
                            audioPlayerViewModel.clearErrorMessage()
                        }
                }

                launch {
                    if (resources.getBoolean(R.bool.isTablet) && authHelper.isValid()) {
                        // Observer multi column mode only when tablet and logged in
                        tazApiCssDataStore.multiColumnMode.asLiveData()
                            .observe(viewLifecycleOwner) { isMultiColumn ->
                                viewBinding.webviewPagerViewpager.isUserInputEnabled =
                                    !isMultiColumn
                                viewBinding.collapsingToolbarLayout.pinToolbar(isMultiColumn)
                            }
                    }
                }
            }
        }
        // show header
        viewBinding.headerCustom.root.isVisible = true

        // Adjust padding when we have cutout display
        lifecycleScope.launch {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding.collapsingToolbarLayout.setPadding(0, extraPadding, 0, 0)
            }
        }
    }

    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
            articlePagerAdapter = BookmarkPagerAdapter()
            adapter = articlePagerAdapter
        }

        articlePagerAdapter.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                articlePagerAdapter.getArticleStub(
                    viewBinding.webviewPagerViewpager.currentItem
                )?.let {
                    rebindBottomNavigation(it)
                }
            }
        })
    }

    private fun rebindBottomNavigation(articleToBindTo: ArticleOperations) {
        // show the share icon always when in public issues (as it shows a popup that the user should log in)
        // OR when an onLink link is provided
        articleBottomActionBarNavigationHelper.setShareIconVisibility(articleToBindTo)
        isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
        isBookmarkedLiveData =
            bookmarkRepository.createBookmarkStateFlow(articleToBindTo.key).asLiveData()
        isBookmarkedLiveData?.observe(this@BookmarkPagerFragment, isBookmarkedObserver)

    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // If the pager is empty (no bookmarks left) we want to pop it off the stack and return to the last fragment
            if (articlePagerAdapter.articleStubs.isEmpty()) {
                this@BookmarkPagerFragment.parentFragmentManager.popBackStack()
                return
            }
            val articleStub = articlePagerAdapter.getArticleStub(position)
            articleStub?.let {
                viewModel.articleFileNameLiveData.value = it.articleFileName
                rebindBottomNavigation(it)
                audioPlayerViewModel.setVisible(it)
            }


            articleBottomActionBarNavigationHelper.apply {
                // show the player button only for articles with audio
                val hasAudio = articleStub?.hasAudio == true
                setArticleAudioVisibility(hasAudio)

                // ensure the action bar is showing when the article changes
                expand(true)
            }
            // ensure the app bar of the webView is shown when article changes
            expandAppBarIfCollapsed()
        }
    }

    private fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> MainActivity.start(requireContext())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentlyDisplayedArticleStub()?.let {
                    viewModel.toggleBookmark(it)
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size ->
                TextSettingsBottomSheetFragment.newInstance()
                    .show(childFragmentManager, TextSettingsBottomSheetFragment.TAG)

            R.id.bottom_navigation_action_audio -> {
                audioPlayerViewModel.handleOnAudioActionOnVisible()
            }
        }
    }

    private fun share() {
        getCurrentlyDisplayedArticleStub()?.let { articleStub ->
            tracker.trackShareArticleEvent(articleStub)
            ShareArticleBottomSheet.newInstance(articleStub)
                .show(parentFragmentManager, ShareArticleBottomSheet.TAG)
        }
    }

    private suspend fun tryScrollToArticle() {
        val articleFileName = viewModel.articleFileNameLiveData.value
        if (
            articleFileName?.startsWith("art") == true &&
            viewModel.bookmarkedArticleStubsFlow.first().map { it.key }.contains(articleFileName)
        ) {
            setHeader(articleFileName)
            log.debug("I will now display $articleFileName")
            withResumed {
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                    }
                }
            }
        }
    }

    private fun getCurrentlyDisplayedArticleStub(): ArticleStub? =
        articlePagerAdapter.getArticleStub(getCurrentPagerPosition())

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position = articlePagerAdapter.articleStubs.indexOfFirst {
            it.key == viewModel.articleFileNameLiveData.value
        }
        return if (position >= 0) {
            position
        } else {
            null
        }
    }

    private inner class BookmarkPagerAdapter : FragmentStateAdapter(
        this@BookmarkPagerFragment
    ) {

        var articleStubs: List<ArticleStub> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun createFragment(position: Int): Fragment {
            val article = articleStubs[position]
            val pagerPosition = position + 1
            val pagerTotal = articleStubs.size
            return ArticleWebViewFragment.newInstance(article, pagerPosition, pagerTotal)
        }

        override fun getItemId(position: Int): Long {
            return articleStubs[position].key.hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return articleStubs.any { itemId == it.key.hashCode().toLong() }
        }

        override fun getItemCount(): Int = articleStubs.size


        fun getArticleStub(position: Int): ArticleStub? {
            return articleStubs.getOrNull(position)
        }

    }

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        articleBottomActionBarNavigationHelper.onDestroyView()
        super.onDestroyView()
    }

    private fun setHeader(displayableKey: String) {
        lifecycleScope.launch {
            val articleStub = articleRepository.getStub(displayableKey)
            articleStub?.let { stub ->
                val issueStub = issueRepository.getIssueStubForArticle(stub.key)

                viewBinding.header.root.isVisible = false

                val position =
                    articlePagerAdapter.articleStubs.indexOf(
                        getCurrentlyDisplayedArticleStub()
                    ) + 1
                val total = articlePagerAdapter.itemCount

                viewBinding.headerCustom.apply {
                    root.isVisible = true
                    indexIndicator.text =
                        getString(
                            R.string.fragment_header_custom_index_indicator,
                            position,
                            total
                        )
                    sectionTitle.text =
                        stub.getSectionStub(requireContext().applicationContext)?.title
                    publishedDate.text = getString(
                        R.string.fragment_header_custom_published_date,
                        determineDateString(stub, issueStub)
                    )
                }
            }
        }
    }

    private fun determineDateString(
        articleOperations: ArticleOperations,
        issueStub: IssueStub?
    ): String {
        if (BuildConfig.IS_LMD) {
            return DateHelper.stringToLocalizedMonthAndYearString(articleOperations.issueDate)
                ?: ""
        } else {
            val fromDate = issueStub?.date?.let { DateHelper.stringToDate(it) }
            val toDate = issueStub?.validityDate?.let { DateHelper.stringToDate(it) }

            return if (fromDate != null && toDate != null) {
                DateHelper.dateToMediumRangeString(fromDate, toDate)
            } else {
                DateHelper.stringToMediumLocalizedString(articleOperations.issueDate) ?: ""
            }
        }
    }

    /**
     * Check if appBarLayout is fully expanded and if not then expand it and show the logo.
     */
    private fun expandAppBarIfCollapsed() {
        val appBarFullyExpanded =
            viewBinding.appBarLayout.height - viewBinding.appBarLayout.bottom == 0

        if (!appBarFullyExpanded) {
            viewBinding.appBarLayout.setExpanded(true, false)
            drawerAndLogoViewModel.setFeedLogo()
        }
    }

    // Helper functions used on from the ArticleWebViewFragment to control the collapsible elements while scrolling programmatically
    override fun getAppBarLayout(): AppBarLayout = viewBinding.appBarLayout
    override fun getBottomNavigationLayout(): View = viewBinding.navigationBottomLayout
}