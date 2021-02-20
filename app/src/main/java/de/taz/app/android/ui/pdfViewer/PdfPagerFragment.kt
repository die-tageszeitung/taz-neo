package de.taz.app.android.ui.pdfViewer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen

class PdfPagerFragment : BaseMainFragment(
    R.layout.fragment_pdf_pager
) {
    private val log by Log

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_pdf_pager

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        pdfPagerViewModel.pdfDataListModel.observe(viewLifecycleOwner, {
            if (it.isNotEmpty()) {
                pdf_viewpager.apply {
                    adapter = activity?.let { it1 -> PdfPagerAdapter(it1) }
                    reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
                    offscreenPageLimit = 2

                    registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            log.verbose("page selected: $position")
                            pdfPagerViewModel.activePosition.value = position
                            super.onPageSelected(position)
                        }
                    })
                }
            }
        })

        pdfPagerViewModel.userInputEnabled.observe(viewLifecycleOwner, { enabled ->
            pdf_viewpager.isUserInputEnabled = enabled
        })

        pdfPagerViewModel.currentItem.observe(viewLifecycleOwner, { position ->
            pdf_viewpager.currentItem = position+1
        })
        loading_screen?.visibility = View.GONE
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

    override fun onDestroyView() {
        pdf_viewpager.adapter = null
        super.onDestroyView()
    }

    /**
     * A simple pager adapter that represents pdfFragment objects, in
     * sequence.
     */
    private inner class PdfPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return PdfRenderFragment(position)
        }

        override fun getItemCount(): Int {
            return pdfPagerViewModel.getAmountOfPdfPages()
        }
    }
}