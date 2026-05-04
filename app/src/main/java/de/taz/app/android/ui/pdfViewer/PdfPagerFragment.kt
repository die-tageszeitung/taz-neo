package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.behavior.HideViewOnScrollBehavior.EDGE_BOTTOM
import com.google.android.material.behavior.HideViewOnScrollBehavior.EDGE_LEFT
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.BurgerMenuCoachMark
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.PdfPageCoachMark
import de.taz.app.android.coachMarks.PdfSelectCoachMark
import de.taz.app.android.coachMarks.PdfZoomCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentPdfPagerBinding
import de.taz.app.android.monkey.getHideViewOnScrollBehavior
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.pdfViewer.mupdf.OnCoordinatesClickedListener
import de.taz.app.android.ui.pdfViewer.mupdf.PageAdapter
import de.taz.app.android.ui.pdfViewer.mupdf.PageView
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The PdfPagerFragment uses a [ReaderView] to render the [PdfPagerViewModel.pdfPageListFlow]
 */
class PdfPagerFragment : BaseMainFragment<FragmentPdfPagerBinding>() {
    private val pdfPagerViewModel: PdfPagerViewModel by viewModels({ requireParentFragment() })
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private lateinit var audioPlayerService: AudioPlayerService
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tracker: Tracker
    val showHelpFabFlow = MutableStateFlow(true)

    private var isReaderViewInitialized = false

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val log by Log

    override fun onAttach(context: Context) {
        super.onAttach(context)
        audioPlayerService = AudioPlayerService.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.readerView?.apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val bottomBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bottom_height)
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom + bottomBarHeight
                    topMargin = insets.top
                }
                windowInsets
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val currentView = viewBinding?.readerView?.displayedView as? PageView
                // Show the FAB if we are not zoomed in
                val showFab = currentView?.scale == 1f
                lifecycleScope.launch {
                    showHelpFabFlow.emit(showFab)
                }
            }
        }

        pdfPagerViewModel.pdfPageListFlow
            .flowWithLifecycle(lifecycle)
            .filterNotNull()
            .onEach { pdfPageList ->
                if (pdfPageList.isEmpty()) {
                    return@onEach
                }

                if (!isReaderViewInitialized) {
                    initReaderView(pdfPageList)
                    hideLoadingScreen()
                } else {
                    updateReaderView(pdfPageList)
                }
            }.launchIn(lifecycleScope)

        pdfPagerViewModel.reloadPdfFlow
            .flowWithLifecycle(lifecycle)
            .onEach {
                val adapter = viewBinding?.readerView?.adapter as? PageAdapter
                adapter?.refresh() // Clears page size cache
                viewBinding?.readerView?.refresh() // Triggers re-layout and re-render
            }.launchIn(lifecycleScope)


        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    showHelpFabFlow.collect {
                        toggleHelpFab(it)
                    }
                }
            }
        }

        pdfPagerViewModel.currentItem.observe(viewLifecycleOwner) { position ->
            // only update currentItem if it has not been swiped
            if (viewBinding?.readerView?.displayedViewIndex != position) {
                viewBinding?.readerView?.displayedViewIndex = position
            }
        }
        initializeDrawerLogos()
        setupFAB()
    }

    override fun onResume() {
        super.onResume()
        viewBinding?.navigationBottomPdf?.let {
            requireActivity().setupBottomNavigation(
                it,
                BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
            )
        }
    }

    private fun initReaderView(pdfPageList: List<Page>) {
        isReaderViewInitialized = true
        // Setup new Adapters
        viewBinding?.readerView?.apply {
            adapter = PageAdapter(requireContext(), pdfPageList)
            setOnCoordinatesClickedListener(onCoordinatesClickedListener)
            setOnPageChangeCallback {
                pdfPagerViewModel.updateCurrentItem(it)
                trackOnPageChange(it)
            }
            pdfPagerViewModel.currentItem.value?.let {
                displayedViewIndex = it
            }
        }
    }

    private fun updateReaderView(pdfPageList: List<Page>) {
        val adapter = viewBinding?.readerView?.adapter as? PageAdapter
        if (adapter != null) {
            val updatedPages = adapter.update(pdfPageList)
            viewBinding?.readerView?.refresh(updatedPages)
        }
    }

    private val onCoordinatesClickedListener =
        OnCoordinatesClickedListener { page, xPage, yPage, xAbs, yAbs ->
            lifecycleScope.launch {
                if (tazApiCssDataStore.tapToScroll.get()) {
                    handleClickWithTapToScroll(page, xPage, yPage, xAbs, yAbs)
                } else {
                    handlePageClick(page, xPage, yPage)
                }
            }
        }

    /**
     * Handle the click on the page.
     *
     * Opens the element that is clicked on the page at the given coordinates (x, y).
     * Where (0,0) is the top left corner of the page and (1,1) is the bottom right corner of the
     * page.
     * If the page has a podcast, all clicks within the page will be intercepted to play the play podcast.
     *
     * @param page - the page on which the click happened
     * @param x - the x coordinate of the click
     * @param y - the y coordinate of the click
     */
    private suspend fun handlePageClick(page: Page, x: Float, y: Float) {
        val issueStub = pdfPagerViewModel.issueStub
        if (page.podcast != null && issueStub != null && x in 0f..1f && y in 0f..1f) {
            // This page has a podcast and the click was anywhere on the pdf page. Trigger playing the PDF
            audioPlayerService.playPodcast(issueStub, page, page.podcast)
        } else {

            val frameList = page.frameList ?: emptyList()
            val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
            frame?.link?.let {
                pdfPagerViewModel.onFrameLinkClicked(it)
            }
        }
    }

    /**
     * Handle a click on the page, if tap to scroll is enabled.
     */
    private suspend fun handleClickWithTapToScroll(
        page: Page,
        xPage: Float,
        yPage: Float,
        xAbs: Float,
        yAbs: Float
    ) {
        val tapBarWidth = resources.getDimension(R.dimen.tap_bar_width).toInt()

        val isPortrait =
            resources.displayMetrics.heightPixels > resources.displayMetrics.widthPixels
        val isInitialScale = (viewBinding?.readerView?.displayedView as? PageView)?.scale == 1f
        val isPanoramaPage = page.type == PageType.panorama

        val isPanoramaSinglePageFocused = isPanoramaPage && isPortrait && isInitialScale

        val isLeftTapBar = xAbs < tapBarWidth
        val isRightTapBar = xAbs > resources.displayMetrics.widthPixels - tapBarWidth

        val isLeftOutside = xPage < 0f
        val isRightOutside = xPage > 1f

        when {
            isLeftTapBar || isLeftOutside -> handleLeftTap(isPanoramaSinglePageFocused)
            isRightTapBar || isRightOutside -> handleRightTap(isPanoramaSinglePageFocused)
            else -> handlePageClick(page, xPage, yPage)
        }
    }

    /**
     * Handle the tap on the left side of the screen or on the area on the left outside the page.
     *
     * @param isPanoramaSinglePageFocused - true if the panorama page is focused and on side of the
     * panorama page is shown
     */
    private fun handleLeftTap(isPanoramaSinglePageFocused: Boolean) {
        viewBinding?.readerView?.let { readerView ->
            val isRightPageShown = readerView.displayedView.right == readerView.width
            if (isPanoramaSinglePageFocused && isRightPageShown) {
                readerView.scrollToLeftSide()
            } else {
                readerView.moveToPrevious()
            }
        }
    }

    /**
     * Handle the tap on the right side of the screen or on the area on the right outside the page.
     *
     * @param isPanoramaSinglePageFocused - true if the panorama page is focused and on side of the
     * panorama page is shown
     */
    private fun handleRightTap(isPanoramaSinglePageFocused: Boolean) {
        viewBinding?.readerView?.let { readerView ->
            val isLeftPageShown = readerView.displayedView.left == 0
            if (isPanoramaSinglePageFocused && isLeftPageShown) {
                readerView.scrollToRightSide()
            } else {
                readerView.moveToNext()
            }
        }
    }

    private fun hideLoadingScreen() {
        val pdfLoadingScreenRoot = viewBinding?.pdfLoadingScreen?.root
        pdfLoadingScreenRoot?.animate()?.apply {
            alpha(0f)
            duration = LOADING_SCREEN_FADE_OUT_TIME
            withEndAction {
                pdfLoadingScreenRoot.visibility = View.GONE
            }
        }
    }

    private fun trackOnPageChange(position: Int) = CoroutineScope(Dispatchers.Default).launch {
        val issueStub = pdfPagerViewModel.issueStub
        val page = pdfPagerViewModel.pdfPageListFlow.first()?.getOrNull(position)

        if (issueStub != null && page != null) {
            val pagina = page.pagina ?: (position + 1).toString()
            tracker.trackPdfPageScreen(issueStub.issueKey, pagina)
            // Track when ads are on the page
            page.adIdList?.forEach {
                log.debug("Track ad on page $it, ${issueStub.date}, Seite ${page.pagina}")
                tracker.trackPageAdShown(it, issueStub.date, "Seite ${page.pagina}")
            }
        } else {
            log.warn("Could not get page for position=$position")
        }
    }

    private fun initializeDrawerLogos() = viewBinding?.apply {
        lifecycleScope.launch {
            (parentFragment as? PdfPagerWrapperFragment)?.drawerViewController?.apply {
                initialize()
                ensureFeedLogo(feedLogo)
                ensureBurgerIcon(burgerWrapper, burgerLogo)
            }

            // Adjust padding when we have cutout display
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding?.feedLogo?.translationY += extraPadding
                viewBinding?.burgerWrapper?.translationY += extraPadding
            }

            feedLogo.getHideViewOnScrollBehavior()?.apply {
                setViewEdge(EDGE_LEFT)
                slideOut(feedLogo)
            }

            feedLogo.setOnClickListener {
                tracker.trackDrawerOpenEvent(dragged = false)
                drawerAndLogoViewModel.openDrawer()
            }
            burgerLogo.setOnClickListener {
                tracker.trackDrawerOpenEvent(dragged = false)
                drawerAndLogoViewModel.openDrawer()
            }

        }
    }

    private fun setupFAB() {
        viewBinding?.pdfPagerFabHelp?.let { floatingActionButton ->

            ViewCompat.setOnApplyWindowInsetsListener(floatingActionButton) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val marginBottomFromDimens =
                    resources.getDimensionPixelSize(R.dimen.fab_margin)
                val bottomBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bottom_height)
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom + bottomBarHeight + marginBottomFromDimens
                }

                // Return CONSUMED if you don't want the window insets to keep passing
                // down to descendant views.
                WindowInsetsCompat.CONSUMED
            }
            floatingActionButton.setOnClickListener {
                log.verbose("show coach marks in section pager")
                showCoachMarks()
            }

            floatingActionButton.getHideViewOnScrollBehavior()?.setViewEdge(EDGE_BOTTOM)

            issueContentViewModel.fabHelpEnabledFlow
                .flowWithLifecycle(lifecycle)
                .onEach {
                    floatingActionButton.isVisible = it
                }.launchIn(lifecycleScope)
        }
    }

    private fun showCoachMarks() {
        val burgerMenuCoachMark =
            BurgerMenuCoachMark.create(requireActivity().findViewById(R.id.drawer_logo))

        val pdfPageCoachMark = PdfPageCoachMark()
        val pdfSelectCoachMark = PdfSelectCoachMark()
        val pdfZoomCoachMark = PdfZoomCoachMark()

        val coachMarks = listOf(
            burgerMenuCoachMark,
            pdfPageCoachMark,
            pdfSelectCoachMark,
            pdfZoomCoachMark,
        )
        CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
    }
    private suspend fun toggleHelpFab(show: Boolean) {
        if (issueContentViewModel.fabHelpEnabledFlow.first()) {
            if (show) {
                viewBinding?.pdfPagerFabHelp?.show()
            } else {
                viewBinding?.pdfPagerFabHelp?.hide()
            }
        }
    }
}