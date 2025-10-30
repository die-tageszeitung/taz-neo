package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.TAP_ICON_FADE_OUT_TIME
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.audioPlayer.ArticleAudioPlayerViewModel
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.ArticleAudioCoachMark
import de.taz.app.android.coachMarks.ArticleBookmarkCoachMark
import de.taz.app.android.coachMarks.ArticleHomeCoachMark
import de.taz.app.android.coachMarks.ArticleImageCoachMark
import de.taz.app.android.coachMarks.ArticleImagePagerCoachMark
import de.taz.app.android.coachMarks.ArticleSectionCoachMark
import de.taz.app.android.coachMarks.ArticleShareCoachMark
import de.taz.app.android.coachMarks.ArticleSizeCoachMark
import de.taz.app.android.coachMarks.ArticleTapToScrollCoachMark
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.TazLogoCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentWebviewArticlePagerBinding
import de.taz.app.android.monkey.pinToolbar
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.MultiColumnModeBottomSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsBottomSheetFragment
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerViewModel
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment.Companion.ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME
import de.taz.app.android.ui.share.ShareArticleBottomSheet
import de.taz.app.android.ui.webview.ArticleWebViewFragment.CollapsibleLayoutProvider
import de.taz.app.android.ui.webview.HelpFabViewModel
import de.taz.app.android.ui.webview.TapIconsViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs

class ArticlePagerFragment : BaseMainFragment<FragmentWebviewArticlePagerBinding>(), BackFragment,
    CollapsibleLayoutProvider {

    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val pdfPagerViewModel: PdfPagerViewModel by viewModels({ requireParentFragment() })
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()
    private val audioPlayerViewModel: ArticleAudioPlayerViewModel by viewModels()
    private val tapIconsViewModel: TapIconsViewModel by activityViewModels()
    private val helpFabViewModel: HelpFabViewModel by activityViewModels()

    private lateinit var articleRepository: ArticleRepository
    private lateinit var authHelper: AuthHelper
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private lateinit var issueRepository: IssueRepository
    private lateinit var pageRepository: PageRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService

    private val articleBottomActionBarNavigationHelper =
        ArticleBottomActionBarNavigationHelper(::onBottomNavigationItemClicked)

    private var hasBeenSwiped = false
    private var isBookmarkedLiveData: LiveData<Boolean>? = null
    private var currentAppBarOffset = 0
    private var lockOffsetChangedListener = false

    private var sectionChangeHandler: SectionChangeHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        pageRepository = PageRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleBottomActionBarNavigationHelper
            .setBottomNavigationFromContainer(viewBinding.navigationBottomLayout)

        if (resources.getBoolean(R.bool.isTablet)) {
            articleBottomActionBarNavigationHelper.fixToolbarForever()
        }

        viewBinding.webviewPagerViewpager.apply {

            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)

            (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
        }
        viewBinding.loadingScreen.root.visibility = View.GONE

        sectionChangeHandler =
            SectionChangeHandler(viewBinding.webviewPagerViewpager, viewBinding.appBarLayout)

        val isArticleActiveModeFlow = issueContentViewModel.activeDisplayModeFlow.map {
            it == IssueContentDisplayMode.Article
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    issueContentViewModel.articleListFlow.collect { articleStubsWithSectionKey ->
                        if (
                            articleStubsWithSectionKey.map { it.articleStub.key } !=
                            (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.map { it.key }
                        ) {
                            viewBinding.webviewPagerViewpager.adapter =
                                ArticlePagerAdapter(
                                    articleStubsWithSectionKey,
                                    this@ArticlePagerFragment
                                )
                        }
                    }
                }
                launch {
                    combine(
                        issueContentViewModel.displayableKeyFlow,
                        issueContentViewModel.articleListFlow
                    ) { displayableKey, articleList ->
                        tryScrollToArticle(displayableKey, articleList)
                        setHeader(displayableKey)
                    }.collect {}
                }

                launch {
                    isArticleActiveModeFlow.collect {
                        if (!it)
                            hasBeenSwiped = false
                    }
                }

                launch {
                    isArticleActiveModeFlow.filter { it }.collect {
                        if (resources.getBoolean(R.bool.isTablet) && authHelper.isValid()) {
                            // Observer multi column mode only when tablet and logged in
                            tazApiCssDataStore.multiColumnMode.asLiveData()
                                .observe(viewLifecycleOwner) { isMultiColumn ->
                                    viewBinding.collapsingToolbarLayout.pinToolbar(isMultiColumn)
                                }
                            maybeShowMultiColumnBottomSheet()
                        }
                    }
                }

                launch {
                    issueContentViewModel.goNextArticle.collect {
                        if (it) {
                            viewBinding.webviewPagerViewpager.currentItem =
                                getCurrentPagerPosition() + 1
                            issueContentViewModel.goNextArticle.value = false
                        }
                    }
                }

                launch {
                    issueContentViewModel.goPreviousArticle.collect {
                        if (it) {
                            viewBinding.webviewPagerViewpager.currentItem =
                                getCurrentPagerPosition() - 1
                            issueContentViewModel.goPreviousArticle.value = false
                        }
                    }
                }

                launch {
                    issueContentViewModel.issueKeyAndDisplayableKeyFlow.collect {
                        if (it != null) {
                            audioPlayerViewModel.visibleIssueKey = it.issueKey
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
                    audioPlayerViewModel.errorMessageFlow.filterNotNull().collect { message ->
                        toastHelper.showToast(message, long = true)
                        audioPlayerViewModel.clearErrorMessage()
                    }
                }

                launch {
                    tapIconsViewModel.showTapIconsFlow.collect {
                        if (it) {
                            showTapIcons()
                        } else {
                            hideTapIcons()
                        }
                    }
                }
                launch {
                    helpFabViewModel.showHelpFabFlow.collect {
                        toggleHelpFab(it)
                    }
                }
            }
        }
        setupDrawerLogoGhost()
        setupHeader()
        setupViewPager()
        setupFAB()
    }

    private fun showCoachMarks() {
            val tazLogoCoachMark =
                TazLogoCoachMark.create(requireActivity().findViewById(R.id.drawer_logo))

            val bookmarkCoachMark = ArticleBookmarkCoachMark.create(
                viewBinding.navigationBottomLayout
                    .findViewById<View?>(R.id.bottom_navigation_action_bookmark)!!
                    .findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)
            )

            val homeCoachMark = ArticleHomeCoachMark.create(
                viewBinding.navigationBottomLayout
                    .findViewById<View?>(R.id.bottom_navigation_action_home_article)!!
                    .findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)
            )

            val textSizeCoachMark = ArticleSizeCoachMark.create(
                viewBinding.navigationBottomLayout
                    .findViewById<View?>(R.id.bottom_navigation_action_size)!!
                    .findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)
            )

            val shareCoachMark = ArticleShareCoachMark.create(
                viewBinding.navigationBottomLayout
                    .findViewById<View?>(R.id.bottom_navigation_action_share)!!
                    .findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)
            )

            val audioCoachMark = ArticleAudioCoachMark.create(
                viewBinding.navigationBottomLayout
                    .findViewById<View?>(R.id.bottom_navigation_action_audio)!!
                    .findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)
            )

            val articleImageCoachMark = ArticleImageCoachMark()
            val articleTapToScrollCoachMark = ArticleTapToScrollCoachMark()
            val articleImagePagerCoachMark = ArticleImagePagerCoachMark()

            val articleSectionCoachMark = ArticleSectionCoachMark.create(
                viewBinding.header.section, viewBinding.header.section.text.toString()
            )

            val coachMarks = mutableListOf(
                tazLogoCoachMark,
                homeCoachMark,
                bookmarkCoachMark,
                shareCoachMark,
                audioCoachMark,
                textSizeCoachMark,
                articleImageCoachMark,
                articleImagePagerCoachMark,
            )

        lifecycleScope.launch {
            if (tazApiCssDataStore.multiColumnMode.get()) {
                coachMarks.add(0, articleTapToScrollCoachMark)
            }
            val appBarFullyExpanded = viewBinding.appBarLayout.height - viewBinding.appBarLayout.bottom == 0
            if (appBarFullyExpanded) {
                coachMarks.add(articleSectionCoachMark)
            }
            CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
        }
    }

    private suspend fun maybeShowMultiColumnBottomSheet() {
        val alreadyShown =
            generalDataStore.multiColumnModeBottomSheetAlreadyShown.get()
        if (!alreadyShown && !tazApiCssDataStore.multiColumnMode.get())
            if (childFragmentManager.findFragmentByTag(
                    MultiColumnModeBottomSheetFragment.TAG
                ) == null
            ) {
                MultiColumnModeBottomSheetFragment().show(
                    childFragmentManager,
                    MultiColumnModeBottomSheetFragment.TAG
                )
            }
    }

    override fun onResume() {
        super.onResume()
        updateDrawerLogoByCurrentAppBarOffset()
    }

    private fun updateDrawerLogoByCurrentAppBarOffset() {
        lifecycleScope.launch {
            val isMultiColumnMode = tazApiCssDataStore.multiColumnMode.get()
            if (!isMultiColumnMode) {
                val percentToMorph =
                    -currentAppBarOffset.toFloat() / viewBinding.appBarLayout.height.toFloat()
                drawerAndLogoViewModel.morphLogoByPercent(percentToMorph.coerceIn(0f, 1f))
            }
        }
    }

    private fun showTapIcons() {
        viewBinding.apply {
            leftTapIcon.animate().alpha(1f).duration =
                TAP_ICON_FADE_OUT_TIME
            rightTapIcon.animate().alpha(1f).duration =
                TAP_ICON_FADE_OUT_TIME
        }
    }

    private fun hideTapIcons() {
        viewBinding.apply {
            leftTapIcon.animate().alpha(0f).duration =
                TAP_ICON_FADE_OUT_TIME
            rightTapIcon.animate().alpha(0f).duration =
                TAP_ICON_FADE_OUT_TIME
        }
    }
    private suspend fun toggleHelpFab(show: Boolean) {
        if (issueContentViewModel.fabHelpEnabledFlow.first()) {
            val fab = viewBinding.articlePagerFabHelp
            val layoutParams = fab.layoutParams
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val behavior = layoutParams.behavior
                if (behavior is HideBottomViewOnScrollBehavior) {
                    if (show) {
                        behavior.slideUp(fab)
                    } else {
                        behavior.slideDown(fab)
                    }
                }
            }
        }
    }

    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    /**
     * On edge to edge we need to properly update the margins of the FAB:
     */
    private fun setupFAB() {
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.articlePagerFabHelp) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            val marginBottomFromDimens = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginBottomFromDimens
            }

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        viewBinding.articlePagerFabHelp.setOnClickListener {
            // Expand the tool bar as some coachmarks are pointing to it
            articleBottomActionBarNavigationHelper.expand(true)

            log.verbose("show coach marks in article pager")
            showCoachMarks()
        }

        issueContentViewModel.fabHelpEnabledFlow
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.articlePagerFabHelp.isVisible = it
            }.launchIn(lifecycleScope)
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        private var wasUserInputEnabledOnArticles: Boolean = true

        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked)
        }

        override fun onPageSelected(position: Int) {
            tapIconsViewModel.hideTapIcons()
            lifecycleScope.launch {
                toggleHelpFab(true)
                helpFabViewModel.showHelpFab()
            }

            val adapter = (viewBinding.webviewPagerViewpager.adapter as ArticlePagerAdapter)
            val selectedItem = adapter.articlePagerItems[position]
            val prevItem = lastPage?.let { adapter.articlePagerItems[it] }

            when (selectedItem) {
                is ArticlePagerItem.ArticleRepresentation -> {
                    onArticleSelected(
                        position, selectedItem.art.articleStub
                    )

                    if (prevItem !is ArticlePagerItem.ArticleRepresentation) {
                        // Restore the default behavior for the pager
                        // Must be called after the [onArticleSelected] block, so that the displayable is correct
                        issueContentViewModel.currentDisplayable?.let { setHeader(it) }
                        viewBinding.webviewPagerViewpager.isUserInputEnabled =
                            wasUserInputEnabledOnArticles
                    }
                    wasUserInputEnabledOnArticles =
                        viewBinding.webviewPagerViewpager.isUserInputEnabled
                    // always show taz logo when on new article:
                    drawerAndLogoViewModel.setFeedLogo()
                }

                is ArticlePagerItem.Tom -> {
                    if (prevItem !is ArticlePagerItem.Tom) {
                        // If the previous page was not a tom, we have to setup the header
                        setHeaderForTom()
                        // and ensure the viewpager is enabled
                        viewBinding.webviewPagerViewpager.isUserInputEnabled = true
                        // ensure the action bar is showing when the tom is views
                        // as tom is not vertically scrollable the coordinator layout won't trigger to show/hide it
                        articleBottomActionBarNavigationHelper.expand(animate = true)
                        expandAppBarIfCollapsed()
                    }
                    lastPage = position
                }
            }
        }

        private fun onArticleSelected(position: Int, nextStub: ArticleStub) {
            if (lastPage != null && lastPage != position) {
                // if position has been changed by 1 (swipe to left or right)
                if (abs(position - lastPage!!) == 1) {
                    hasBeenSwiped = true
                }
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyFlow.value?.issueKey,
                    nextStub
                ) { issueKey, displayable ->
                    log.debug("After swiping select displayable to ${displayable.key} (${displayable.title})")
                    if (issueContentViewModel.activeDisplayModeFlow.value == IssueContentDisplayMode.Article) {
                        issueContentViewModel.setDisplayable(
                            IssueKeyWithDisplayableKey(
                                issueKey,
                                displayable.key
                            )
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
                                pdfPagerViewModel.goToPdfPage(it, saveLastDisplayable = false)
                            }
                        }
                    }
                }
            }
            lastPage = position

            viewLifecycleOwner.lifecycleScope.launch {
                // show the share icon always when in public issues (as it shows a popup that the user should log in)
                // OR when an onLink link is provided
                articleBottomActionBarNavigationHelper.setShareIconVisibility(nextStub)

                isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                isBookmarkedLiveData =
                    bookmarkRepository.createBookmarkStateFlow(nextStub.articleFileName)
                        .asLiveData()
                isBookmarkedLiveData?.observe(this@ArticlePagerFragment, isBookmarkedObserver)
            }

            audioPlayerViewModel.setVisible(nextStub)

            articleBottomActionBarNavigationHelper.apply {
                // show the player button only for articles with audio
                setArticleAudioVisibility(nextStub.hasAudio)
                // ensure the action bar is showing when the article changes
                expand(true)
            }
            // ensure the app bar of the webView is shown when article changes
            expandAppBarIfCollapsed()
        }


        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int,
        ) {
            sectionChangeHandler?.onPageScrolled(
                position, positionOffset, positionOffsetPixels
            )
            // We don't want the offsetChangedListener of the appBar to be triggered when paging:
            lockOffsetChangedListener = positionOffset > 0f && positionOffset < 1f
        }

        override fun onPageScrollStateChanged(state: Int) {
            val activateScrollBar = state == ViewPager2.SCROLL_STATE_IDLE
            lastPage?.let {
                sectionChangeHandler?.activateScrollBar(it, activateScrollBar)
            }
            super.onPageScrollStateChanged(state)
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

    override fun onBackPressed(): Boolean {
        try {
            val isImprint =
                (getCurrentArticlePagerItem() as? ArticlePagerItem.ArticleRepresentation)?.art?.articleStub?.isImprint() == true
            val isTom = getCurrentArticlePagerItem() is ArticlePagerItem.Tom

            return if (isImprint || isTom) {
                // go back by popping backstack
                requireActivity().supportFragmentManager.popBackStackImmediate()
                true
            } else {
                false
            }
        } catch (npe: NullPointerException) {
            log.warn("We got a NPE when trying to call getCurrentArticlePagerItem(). Probably viewBinding is gone.")
            SentryWrapper.captureException(npe)
            return false
        }
    }

    private fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> (requireActivity() as? MainActivity)?.showHome()

            R.id.bottom_navigation_action_bookmark -> {
                when (val currentItem = getCurrentArticlePagerItem()) {
                    is ArticlePagerItem.ArticleRepresentation ->
                        if (currentItem.art.articleStub.isImprint()) {
                            toastHelper.showToast(R.string.toast_imprint_not_possible_to_bookmark)
                        } else {
                            toggleBookmark(currentItem.art.articleStub)
                        }

                    is ArticlePagerItem.Tom -> toastHelper.showToast(R.string.toast_tom_not_possible_to_bookmark)
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                TextSettingsBottomSheetFragment.newInstance()
                    .show(childFragmentManager, TextSettingsBottomSheetFragment.TAG)
            }

            R.id.bottom_navigation_action_audio ->
                audioPlayerViewModel.handleOnAudioActionOnVisible()
        }
    }

    private fun toggleBookmark(article: ArticleOperations) {
        lifecycleScope.launch {
            val isBookmarked = bookmarkRepository.toggleBookmarkAsync(article).await()
            if (isBookmarked) {
                SnackBarHelper.showBookmarkSnack(
                    context = requireContext(),
                    view = viewBinding.webviewPagerViewpager.rootView,
                    anchor = getBottomNavigationLayout(),
                )
            } else {
                SnackBarHelper.showDebookmarkSnack(
                    context = requireContext(),
                    view = viewBinding.webviewPagerViewpager.rootView,
                    anchor = getBottomNavigationLayout(),
                )
            }
        }
    }

    private fun share() {
        when (val currentItem = getCurrentArticlePagerItem()) {
            is ArticlePagerItem.ArticleRepresentation -> {
                val articleStub = currentItem.art.articleStub
                tracker.trackShareArticleEvent(articleStub)
                ShareArticleBottomSheet.newInstance(articleStub)
                    .show(parentFragmentManager, ShareArticleBottomSheet.TAG)
            }

            is ArticlePagerItem.Tom ->
                toastHelper.showToast(R.string.toast_tom_not_possible_to_share)
        }
    }

    private suspend fun tryScrollToArticle(
        articleKey: String,
        articleStubs: List<ArticleStubWithSectionKey>
    ) {
        if (
            articleKey.startsWith("art") && articleStubs.map { it.articleStub.key }
                .contains(articleKey)
        ) {
            if (articleKey != getCurrentArticleStub()?.key) {
                log.debug("I will now display $articleKey")
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        try {
                            viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                        } catch (npe: NullPointerException) {
                            log.warn("We lost the viewBindings web view. Abort horizontal scrolling…")
                            SentryWrapper.captureException(npe)
                        }
                    }
                }
            }
        }
    }

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private suspend fun getSupposedPagerPosition(): Int? {
        val position =
            (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.indexOfFirst {
                it.key == issueContentViewModel.displayableKeyFlow.first()
            }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentArticlePagerItem(): ArticlePagerItem {
        return (viewBinding.webviewPagerViewpager.adapter as ArticlePagerAdapter).articlePagerItems[getCurrentPagerPosition()]
    }

    private fun getCurrentArticleStub(): ArticleStub? {
        return (getCurrentArticlePagerItem() as? ArticlePagerItem.ArticleRepresentation)?.art?.articleStub
    }

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        sectionChangeHandler = null
        articleBottomActionBarNavigationHelper.onDestroyView()
        super.onDestroyView()
    }

    // region header functions
    private fun setupHeader() {
        viewBinding.header.root.visibility = View.VISIBLE

        viewBinding.appBarLayout.apply {
            addOnOffsetChangedListener { _, verticalOffset ->
                if (!lockOffsetChangedListener) {
                    currentAppBarOffset = verticalOffset
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        updateDrawerLogoByCurrentAppBarOffset()
                    }
                }
            }
        }

        // Adjust padding when we have cutout display
        lifecycleScope.launch {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding.collapsingToolbarLayout.setPadding(0, extraPadding, 0, 0)
            }
        }
    }

    private fun setHeader(displayableKey: String) {
        lifecycleScope.launch {
            val articleStub = articleRepository.getStub(displayableKey)
            articleStub?.let { stub ->
                val issueStub = issueRepository.getIssueStubForArticle(stub.key)
                val sectionStub = stub.getSectionStub(requireContext().applicationContext)
                // only the imprint should have no section
                if (sectionStub?.title == null) {
                    setHeaderForImprint()
                } else if (BuildConfig.IS_LMD) {
                    val firstPage = stub.pageNameList.firstOrNull()
                    if (firstPage !== null) {
                        setHeaderWithPage(firstPage)
                    } else {
                        hideHeaderWithPage()
                    }
                } else {
                    val index = stub.getIndexInSection(requireContext().applicationContext) ?: 0
                    val count = articleRepository.getSectionArticleStubListByArticleName(
                        stub.key
                    ).size
                    setHeaderForSection(index, count, sectionStub, stub.pageNameList.firstOrNull())
                }

                if (issueStub?.isWeekend == true) {
                    applyWeekendTypefacesToHeader()
                }
            }
        }
    }

    private fun setHeaderForTom() {
        viewBinding.header.apply {
            section.apply {
                text = getString(R.string.article_tom_at_the_end_title)
                setOnClickListener(null)
            }
            articleNum.text = ""
        }
    }

    private fun setHeaderForImprint() {
        viewBinding.header.apply {
            section.apply {
                text = getString(R.string.imprint)
                setOnClickListener(null)
            }
            articleNum.text = ""
        }
    }

    private fun setHeaderForSection(
        index: Int,
        count: Int,
        sectionStub: SectionStub?,
        pageFileName: String?,
    ) {
        viewBinding.header.apply {
            section.apply {
                text = sectionStub?.title
                setOnClickListener {
                    lifecycleScope.launch {
                        if (generalDataStore.pdfMode.get() && pageFileName != null) {
                            pdfPagerViewModel.goToPdfPage(pageFileName)
                            parentFragmentManager.popBackStack(
                                ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
                                POP_BACK_STACK_INCLUSIVE
                            )
                        } else {
                            goBackToSection(sectionStub)
                        }
                    }
                }
            }
            articleNum.text = getString(
                R.string.fragment_header_article_index_section_count, index, count
            )
        }
    }

    /**
     * Set the header for the article web view.
     *
     * @param pageFileName - the page file name, e.g.: 's00856508.pdf'
     */
    private suspend fun setHeaderWithPage(pageFileName: String) {
        // A 'pdf-page' could be double page, where a pagina could look like this '9-10', due to
        // this we added the split and get(0) to get the first page number.
        val firstPageNum = pageRepository.getStub(pageFileName)?.pagina?.split('-')?.get(0)
        viewBinding.header.apply {
            articleNum.isVisible = false
            section.apply {
                text = getString(R.string.fragment_header_article_pagina, firstPageNum)
                setOnClickListener {
                    pdfPagerViewModel.goToPdfPage(pageFileName)
                    parentFragmentManager.popBackStack(
                        ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
                        POP_BACK_STACK_INCLUSIVE
                    )
                }
            }
        }
    }


    private fun hideHeaderWithPage() {
        viewBinding.header.apply {
            section.isVisible = false
            articleNum.isVisible = false
        }
    }

    private fun applyWeekendTypefacesToHeader() {
        val context = context ?: return
        viewBinding.header.apply {
            section.typeface = ResourcesCompat.getFont(context, R.font.appFontKnileSemiBold)
            articleNum.typeface = ResourcesCompat.getFont(context, R.font.appFontKnileRegular)
        }
    }
    // endregion

    private fun goBackToSection(sectionStub: SectionStub?) = lifecycleScope.launch {
        sectionStub?.let {
            issueRepository.getIssueStubForSection(sectionStub.sectionFileName)?.let { issueStub ->
                lifecycleScope.launch {
                    issueContentViewModel.setDisplayable(
                        issueStub.issueKey,
                        sectionStub.sectionFileName
                    )
                }
            }
        }
    }

    /**
     * Scroll to the next page/item.
     * Does nothing if the pager is already on the last page.
     */
    fun pageRight() {
        val currentPosition = getCurrentPagerPosition()
        val total = viewBinding.webviewPagerViewpager.adapter?.itemCount ?: 0
        if (currentPosition < total - 1) {
            viewBinding.webviewPagerViewpager.setCurrentItem(currentPosition + 1, false)
        }
    }

    /**
     * Scroll to the previous page/item, if there is one.
     * Does nothing if the pager is already on the first page.
     */
    fun pageLeft() {
        val currentPosition = getCurrentPagerPosition()
        if (currentPosition > 0) {
            viewBinding.webviewPagerViewpager.setCurrentItem(currentPosition - 1, false)
        }
    }

    private fun setupDrawerLogoGhost() {
        viewBinding.articlePagerDrawerLogoGhost.setOnClickListener {
            tracker.trackDrawerOpenEvent(dragged = false)
            drawerAndLogoViewModel.openDrawer()
        }
    }

    /**
     * Scroll to the very first page/item.
     */
    fun pageToFirst() {
        viewBinding.webviewPagerViewpager.setCurrentItem(0, true)
    }

    override fun getAppBarLayout(): AppBarLayout = viewBinding.appBarLayout
    override fun getBottomNavigationLayout(): View = viewBinding.navigationBottomLayout
}