package de.taz.app.android.ui.pdfViewer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.settings.SettingsActivity
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PdfPagerFragment : BaseMainFragment(
    R.layout.fragment_pdf_pager
) {

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_pdf_pager

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner, {
            if (it.isNotEmpty()) {
                pdf_viewpager.apply {
                    adapter = PdfPagerAdapter(requireActivity())
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
        })

        pdfPagerViewModel.userInputEnabled.observe(viewLifecycleOwner, { enabled ->
            pdf_viewpager.isUserInputEnabled = enabled
        })

        pdfPagerViewModel.requestDisallowInterceptTouchEvent.observe(viewLifecycleOwner, { disallow ->
            val delay = if (!disallow) {
                100L
            } else
                0L
            lifecycleScope.launch {
                delay(delay)
                pdf_viewpager.isUserInputEnabled = !disallow
                pdf_viewpager.requestDisallowInterceptTouchEvent(disallow)
            }
        })

        pdfPagerViewModel.currentItem.observe(viewLifecycleOwner, { position ->
            // only update currentItem if it has not been swiped
            if(pdf_viewpager.currentItem != position) {
                pdf_viewpager.setCurrentItem(position, true)
            }
        })
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
     * A simple pager adapter that represents pdfFragment objects, in sequence.
     */
    private inner class PdfPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return PdfRenderFragment.create(position)
        }

        override fun getItemCount(): Int {
            return pdfPagerViewModel.getAmountOfPdfPages()
        }
    }
}