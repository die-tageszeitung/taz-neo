package de.taz.app.android.ui.pdfViewer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.Page
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentPdfPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The PdfPagerFragment creates a [ViewPager2] and populates it with the
 * [PdfPagerViewModel.pdfPageList]
 */
class PdfPagerFragment : BaseMainFragment<FragmentPdfPagerBinding>() {
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_pdf_pager

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private val log by Log

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                pdf_viewpager.apply {
                    adapter = PdfPagerAdapter(this@PdfPagerFragment, it)
                    reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
                    offscreenPageLimit = 2

                    // set position so it does not default to 0
                    pdfPagerViewModel.currentItem.value?.let { position ->
                        setCurrentItem(
                            position,
                            false
                        )
                    }

                    registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            pdfPagerViewModel.updateCurrentItem(position)
                            super.onPageSelected(position)
                        }
                    })
                }
                hideLoadingScreen()
            }
        }

        pdfPagerViewModel.userInputEnabled.observe(viewLifecycleOwner) { enabled ->
            pdf_viewpager.isUserInputEnabled = enabled
        }

        pdfPagerViewModel.requestDisallowInterceptTouchEvent.observe(
            viewLifecycleOwner
        ) { disallow ->
            val delay = if (!disallow) {
                100L
            } else
                0L
            lifecycleScope.launch {
                delay(delay)
                pdf_viewpager.isUserInputEnabled = !disallow
                pdf_viewpager.requestDisallowInterceptTouchEvent(disallow)
            }
        }

        pdfPagerViewModel.currentItem.observe(viewLifecycleOwner) { position ->
            // only update currentItem if it has not been swiped
            if (pdf_viewpager.currentItem != position) {
                pdf_viewpager.setCurrentItem(position, true)
            }
        }

        lifecycleScope.launchWhenResumed {
            pdfPagerViewModel.swipePageFlow
                .collect {
                    val newPosition = when (it) {
                        SwipeEvent.LEFT -> {
                            log.verbose("Swipe event LEFT received")
                            (pdfPagerViewModel.currentItem.value ?: 0) + 1
                        }
                        SwipeEvent.RIGHT -> {
                            log.verbose("Swipe event RIGHT received")
                            (pdfPagerViewModel.currentItem.value ?: 0) - 1
                        }
                    }

                    pdfPagerViewModel.updateCurrentItem(
                        newPosition.coerceIn(0, pdfPagerViewModel.getAmountOfPdfPages() - 1)
                    )
                }
        }

    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> requireActivity().finish()
            R.id.bottom_navigation_action_settings -> {
                Intent(requireActivity(), SettingsActivity::class.java).apply {
                    startActivity(this)
                }
            }
            R.id.bottom_navigation_action_help -> {
                Intent(requireActivity(), WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(this)
                }
            }
        }
    }

    private fun hideLoadingScreen() {
        requireActivity().runOnUiThread {
            pdf_loading_screen?.animate()?.apply {
                alpha(0f)
                duration = LOADING_SCREEN_FADE_OUT_TIME
                withEndAction {
                    pdf_loading_screen?.visibility = View.GONE
                }
            }
            pdfPagerViewModel.hideDrawerLogo.postValue(true)
        }
    }

    /**
     * A simple pager adapter that creates a [PdfRenderFragment] for every [Page] in [pageList]
     */
    private inner class PdfPagerAdapter(fragment: Fragment, private val pageList: List<Page>) :
        FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return PdfRenderFragment.create(pageList[position])
        }

        override fun getItemCount(): Int {
            return pdfPagerViewModel.getAmountOfPdfPages()
        }
    }
}