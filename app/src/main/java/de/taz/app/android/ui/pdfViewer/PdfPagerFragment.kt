package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentPdfPagerBinding
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.pdfViewer.mupdf.OnCoordinatesClickedListener
import de.taz.app.android.ui.pdfViewer.mupdf.PageAdapter
import de.taz.app.android.ui.pdfViewer.mupdf.PageView
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch

/**
 * The PdfPagerFragment uses a [ReaderView] to render the [PdfPagerViewModel.pdfPageList]
 */
class PdfPagerFragment : BaseMainFragment<FragmentPdfPagerBinding>() {

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tracker: Tracker
    private lateinit var audioPlayerService: AudioPlayerService

    private var isReaderViewInitialized = false

    private val log by Log

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        audioPlayerService = AudioPlayerService.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) { pdfPageList ->
            if (pdfPageList.isEmpty()) {
                return@observe
            }

            if (!isReaderViewInitialized) {
                initReaderView(pdfPageList)
                hideLoadingScreen()
            } else {
                updateReaderView(pdfPageList)
            }
        }

        pdfPagerViewModel.currentItem.observe(viewLifecycleOwner) { position ->
            // only update currentItem if it has not been swiped
            if (viewBinding.readerView.displayedViewIndex != position) {
                viewBinding.readerView.displayedViewIndex = position
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottomPdf,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
        )
    }

    private fun initReaderView(pdfPageList: List<Page>) {
        isReaderViewInitialized = true
        // Setup new Adapters
        viewBinding.readerView.apply {
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
        val adapter = viewBinding.readerView.adapter as? PageAdapter
        if (adapter != null) {
            val updatedPages = adapter.update(pdfPageList)
            viewBinding.readerView.refresh(updatedPages)
        }
    }

    private val onCoordinatesClickedListener =
        OnCoordinatesClickedListener { page, xPage, yPage, xAbs, yAbs ->
            viewLifecycleOwner.lifecycleScope.launch {
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
    private fun handlePageClick(page: Page, x: Float, y: Float) {
        val issueStub = pdfPagerViewModel.issueStub
        if (page.podcast != null && issueStub != null && x in 0f..1f && y in 0f..1f) {
            // This page has a podcast and the click was anywhere on the pdf page. Trigger playing the PDF
            audioPlayerService.playPodcast(issueStub, page, page.podcast)
        } else {

            val frameList = page.frameList ?: emptyList()
            val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
            if (frame != null) {
                frame.link?.let {
                    pdfPagerViewModel.onFrameLinkClicked(it)
                }
            }
        }
    }

    /**
     * Handle a click on the page, if tap to scroll is enabled.
     */
    private fun handleClickWithTapToScroll(page: Page, xPage: Float, yPage: Float, xAbs: Float, yAbs: Float) {
        val tapBarWidth = resources.getDimension(R.dimen.tap_bar_width).toInt()

        val isPortrait =
            resources.displayMetrics.heightPixels > resources.displayMetrics.widthPixels
        val isInitialScale = (viewBinding.readerView.displayedView as PageView).scale == 1f
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
        val readerView = viewBinding.readerView
        val isRightPageShown = readerView.displayedView.right == readerView.width
        if (isPanoramaSinglePageFocused && isRightPageShown) {
            readerView.scrollToLeftSide()
        } else {
            readerView.moveToPrevious()
        }
    }

    /**
     * Handle the tap on the right side of the screen or on the area on the right outside the page.
     *
     * @param isPanoramaSinglePageFocused - true if the panorama page is focused and on side of the
     * panorama page is shown
     */
    private fun handleRightTap(isPanoramaSinglePageFocused: Boolean) {
        val readerView = viewBinding.readerView
        val isLeftPageShown = readerView.displayedView.left == 0
        if (isPanoramaSinglePageFocused && isLeftPageShown) {
            readerView.scrollToRightSide()
        } else {
            readerView.moveToNext()
        }
    }

    private fun hideLoadingScreen() {
        val pdfLoadingScreenRoot = viewBinding.pdfLoadingScreen.root
        pdfLoadingScreenRoot.animate().apply {
            alpha(0f)
            duration = LOADING_SCREEN_FADE_OUT_TIME
            withEndAction {
                pdfLoadingScreenRoot.visibility = View.GONE
            }
        }
        drawerAndLogoViewModel.hideLogo()
    }

    private fun trackOnPageChange(position: Int) {
        val issueStub = pdfPagerViewModel.issueStub
        val page = pdfPagerViewModel.pdfPageList.value?.getOrNull(position)

        if (issueStub != null && page != null) {
            val pagina = page.pagina ?: (position + 1).toString()
            tracker.trackPdfPageScreen(issueStub.issueKey, pagina)

        } else {
            log.warn("Could not get page for position=$position")
        }
    }
}