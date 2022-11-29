package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentPdfPagerBinding
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.pdfViewer.mupdf.OnCoordinatesClickedListener
import de.taz.app.android.ui.pdfViewer.mupdf.PageAdapter
import de.taz.app.android.util.Log

/**
 * The PdfPagerFragment uses a [ReaderView] to render the [PdfPagerViewModel.pdfPageList]
 */
class PdfPagerFragment : BaseMainFragment<FragmentPdfPagerBinding>() {
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_home

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private val log by Log

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) { pdfPageList ->
            if (pdfPageList.isNotEmpty()) {
                viewBinding.readerview.apply {
                    adapter = PageAdapter(context, pdfPageList)
                    setOnCoordinatesClickedListener(onCoordinatesClickedListener)
                    setOnPageChangeCallback {
                        pdfPagerViewModel.updateCurrentItem(it)
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
            if (viewBinding.readerview.displayedViewIndex != position) {
                viewBinding.readerview.displayedViewIndex = position
            }
        }
    }

    private val onCoordinatesClickedListener = OnCoordinatesClickedListener { page, x, y ->
        val frameList = page.frameList ?: emptyList()
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        log.error("FrameList: $x $y\n $frameList")
        if (frame != null) {
            frame.link?.let {
                pdfPagerViewModel.onFrameLinkClicked(it)
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
        pdfPagerViewModel.hideDrawerLogo.postValue(true)
    }
}