package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
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
    private val log by Log

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) { pdfPageList ->
            if (pdfPageList.isNotEmpty()) {
                viewBinding.readerView.apply {
                    adapter = PageAdapter(context, pdfPageList)
                    setOnCoordinatesClickedListener(onCoordinatesClickedListener)
                    setOnPageChangeCallback {
                        pdfPagerViewModel.updateCurrentItem(it)
                        trackOnPageChange(it)
                    }
                    pdfPagerViewModel.currentItem.value?.let {
                        displayedViewIndex = it
                    }
                }
                hideLoadingScreen()
            }
        }

        pdfPagerViewModel.currentItem.observe(viewLifecycleOwner) { position ->
            // only update currentItem if it has not been swiped
            if (viewBinding.readerView.displayedViewIndex != position) {
                viewBinding.readerView.displayedViewIndex = position
            }
        }
    }

    private val onCoordinatesClickedListener = OnCoordinatesClickedListener { page, x, y ->
        val frameList = page.frameList ?: emptyList()
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        if (frame != null) {
            frame.link?.let {
                pdfPagerViewModel.onFrameLinkClicked(it)
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                if (tazApiCssDataStore.tapToScroll.get()) {
                    handleTapToScroll(page, x)
                }
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

    private fun handleTapToScroll(page: Page, x: Float) {
        val isPanoramaPage = page.type == PageType.panorama
        val isPortrait =
            resources.displayMetrics.heightPixels > resources.displayMetrics.widthPixels
        val isInitialScale = (viewBinding.readerView.displayedView as PageView).scale == 1f

        if (x < 0.25f) {
            viewBinding.readerView.moveToPrevious()
        } else if (x > 0.75f) {
            viewBinding.readerView.moveToNext()
        } else if (isPanoramaPage && isPortrait && isInitialScale) {
            handleTapToScrollOnPanoramaPage(x)
        }
    }

    /**
     * The tap functionality works differently on panorama pages in portrait mode.
     * On the left side the intercepted [x] to trigger a right swipe is then between [0.375, 0.5]
     * and accordingly on the right side between [0.5, 0.625].
     * Additionally we do not move to the next page but perform a "swipe" to the other page side.
     */
    private fun handleTapToScrollOnPanoramaPage(x: Float) {
        val displayedView = viewBinding.readerView.displayedView
        if (displayedView != null) {
            val onLeftSide = displayedView.left == 0
            val onRightSide = displayedView.right == viewBinding.readerView.width
            if (onLeftSide && 0.5f >= x && x >= 0.375f) {
                viewBinding.readerView.scrollToRightSide()
            } else if (onRightSide && 0.625f >= x && x >= 0.5f) {
                viewBinding.readerView.scrollToLeftSide()
            }
        }
    }

    private fun trackOnPageChange(position: Int) {
        val issue = pdfPagerViewModel.issue
        val page = issue?.pageList?.getOrNull(position)

        if (issue != null && page != null) {
            val pagina = page.pagina ?: (position + 1).toString()
            tracker.trackPdfPageScreen(issue.issueKey, pagina)

        } else {
            log.warn("Could not get page for position=$position")
        }
    }
}