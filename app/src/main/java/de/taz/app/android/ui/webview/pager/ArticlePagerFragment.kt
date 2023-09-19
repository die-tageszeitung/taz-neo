package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
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
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.BuildConfig
import de.taz.app.android.KNILE_REGULAR_RESOURCE_FILE_NAME
import de.taz.app.android.KNILE_SEMIBOLD_RESOURCE_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.audioPlayer.AudioPlayerViewModel
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.ArticleAudioCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ArticlePagerFragment : BaseMainFragment<FragmentWebviewPagerBinding>(), BackFragment {

    private val log by Log

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()
    private val audioPlayerViewModel: AudioPlayerViewModel by viewModels()

    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private lateinit var issueRepository: IssueRepository
    private lateinit var pageRepository: PageRepository
    private lateinit var fontHelper: FontHelper
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService

    private val articleBottomActionBarNavigationHelper =
        ArticleBottomActionBarNavigationHelper(::onBottomNavigationItemClicked)

    private var hasBeenSwiped = false
    private var isBookmarkedLiveData: LiveData<Boolean>? = null
    private var isTabletLandscapeMode = false
    private var currentAppBarOffset = 0
    private var lockOffsetChangedListener = false

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()

    private var sectionChangeHandler: SectionChangeHandler? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        pageRepository = PageRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        fontHelper = FontHelper.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
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

        sectionChangeHandler =
            SectionChangeHandler(viewBinding.webviewPagerViewpager, viewBinding.appBarLayout)

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
                setHeader(it)
            }
        }


        issueContentViewModel.activeDisplayMode.distinctUntilChanged().observe(viewLifecycleOwner) {
            // reset swiped flag on navigating away from article pager
            if (it != IssueContentDisplayMode.Article) {
                hasBeenSwiped = false
            } else {
                // We show here the couch mark for audio of the article. It is at this place because
                // the [ArticlePagerFragment] is always created in the [IssueViewerActivity], even if
                // a section is shown. But the [activeDisplayMode] gives us the indication that we
                // are on an article.
                lifecycleScope.launch {
                    ArticleAudioCoachMark(
                        requireContext(),
                        viewBinding.navigationBottomLayout
                            .findViewById<View?>(R.id.bottom_navigation_action_audio)
                            .findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view)
                    ).maybeShow()
                }
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
        setupHeader()
    }

    override fun onResume() {
        super.onResume()
        if (!isTabletLandscapeMode) {
            updateDrawerLogoByCurrentAppBarOffset()
        }
    }

    private fun updateDrawerLogoByCurrentAppBarOffset() {
        val percentToHide =
            -currentAppBarOffset.toFloat() / viewBinding.appBarLayout.height.toFloat()
        drawerAndLogoViewModel.hideLogoByPercent(percentToHide.coerceIn(0f, 1f))
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
            // ensure the app bar of the webView is shown when article changes
            expandAppBarIfCollapsed()
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
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
            drawerAndLogoViewModel.showLogo()
        }
    }

    override fun onBackPressed(): Boolean {
        val isImprint = getCurrentArticleStub()?.isImprint() ?: false
        return if (isImprint) {
            requireActivity().finish()
            true
        } else {
            false
        }
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
            val isBookmarked = bookmarkRepository.toggleBookmarkAsync(articleStub).await()
            if (isBookmarked) {
                toastHelper.showToast(R.string.toast_article_bookmarked)
            }
            else {
                toastHelper.showToast(R.string.toast_article_debookmarked)
            }
        }
    }

    private fun share() {
        getCurrentArticleStub()?.let { articleStub ->
            val url = articleStub.onlineLink
            url?.let {
                tracker.trackShareArticleEvent(articleStub)
                shareArticle(url, articleStub.title)
            } ?: showSharingNotPossibleDialog()
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
        sectionChangeHandler = null
        articleBottomActionBarNavigationHelper.onDestroyView()
        super.onDestroyView()
    }

    // region header functions
    private fun setupHeader() {
        viewBinding.header.root.visibility = View.VISIBLE
        val isTabletMode = resources.getBoolean(R.bool.isTablet)
        val isLandscape =
            resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        isTabletLandscapeMode = isTabletMode && isLandscape

        // Map the offset of the app bar layout to the logo as it should
        // (but not on tablets in landscape)
        if (isTabletLandscapeMode) {
            drawerAndLogoViewModel.showLogo()
        } else {
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
            val article = articleRepository.get(displayableKey)
            article?.let { art ->
                val issueStub = issueRepository.getIssueStubForArticle(art.key)
                val sectionStub = art.getSectionStub(requireContext().applicationContext)
                // only the imprint should have no section
                if (sectionStub?.title == null) {
                    setHeaderForImprint()
                } else if (BuildConfig.IS_LMD) {
                    val firstPage = art.pageNameList.firstOrNull()
                    if (firstPage !== null) {
                        setHeaderWithPage(firstPage)
                    } else {
                        hideHeaderWithPage()
                    }
                } else {
                    val index = art.getIndexInSection(requireContext().applicationContext) ?: 0
                    val count = articleRepository.getSectionArticleStubListByArticleName(
                        art.key
                    ).size
                    setHeaderForSection(index, count, sectionStub)
                }

                issueStub?.apply {
                    if (isWeekend) {
                        applyWeekendTypefacesToHeader()
                    }
                }
            }
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

    private fun setHeaderForSection(index: Int, count: Int, sectionStub: SectionStub?) {
        viewBinding.header.apply {
            section.apply {
                text = sectionStub?.title
                setOnClickListener {
                    goBackToSection(sectionStub)
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
            section.isVisible = false
            articleNum.apply {
                text = getString(R.string.fragment_header_article_pagina, firstPageNum)
                setOnClickListener {
                    pdfPagerViewModel.goToPdfPage(pageFileName)
                    (activity as? PdfPagerActivity)?.popArticlePagerFragmentIfOpen()
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

    private suspend fun applyWeekendTypefacesToHeader() {
        val weekendTypefaceFileEntry =
            fileEntryRepository.get(KNILE_SEMIBOLD_RESOURCE_FILE_NAME)
        val weekendTypefaceFile = weekendTypefaceFileEntry?.let(storageService::getFile)
        weekendTypefaceFile?.let {
            fontHelper
                .getTypeFace(it)?.let { typeface ->
                    withContext(Dispatchers.Main) {
                        viewBinding.header.section.typeface = typeface
                    }
                }
        }
        val weekendTypefaceFileEntryRegular =
            fileEntryRepository.get(KNILE_REGULAR_RESOURCE_FILE_NAME)
        val weekendTypefaceFileRegular =
            weekendTypefaceFileEntryRegular?.let(storageService::getFile)
        weekendTypefaceFileRegular?.let {
            fontHelper
                .getTypeFace(it)?.let { typeface ->
                    withContext(Dispatchers.Main) {
                        viewBinding.header.articleNum.typeface = typeface
                    }
                }
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
}