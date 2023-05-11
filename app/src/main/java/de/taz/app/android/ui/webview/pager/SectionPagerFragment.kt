package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.HIDE_LOGO_DELAY_MS
import de.taz.app.android.LOGO_ANIMATION_DURATION_MS
import de.taz.app.android.LOGO_PEAK
import de.taz.app.android.LOGO_PEAK_CLICK_PADDING
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.drawer.DrawerLayout
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull

class SectionPagerFragment : BaseMainFragment<FragmentWebviewPagerBinding>() {
    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val sectionDrawerViewModel: SectionDrawerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
        }
        setupViewPager()

        issueContentViewModel.sectionListLiveData.observe(this.viewLifecycleOwner) { sectionStubs ->
            if (
                sectionStubs.map { it.key } !=
                (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.map { it.key }
            ) {
                log.debug("New set of sections: ${sectionStubs.map { it.key }}")
                viewBinding.webviewPagerViewpager.adapter = SectionPagerAdapter(sectionStubs)
                tryScrollToSection()
                viewBinding.loadingScreen.root.visibility = View.GONE
            }
        }

        issueContentViewModel.displayableKeyLiveData.observe(this.viewLifecycleOwner) {
            tryScrollToSection()
        }
    }

    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        override fun onPageSelected(position: Int) {
            val sectionStubs = (viewBinding.webviewPagerViewpager.adapter as SectionPagerAdapter).sectionStubs
            // if we are beyond last position we are the imprint
            val isImprint = position == sectionStubs.size
            if (lastPage != null && lastPage != position && !isImprint) {
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                    sectionStubs[position]
                ) { issueKey, displayable ->
                    if (issueContentViewModel.activeDisplayMode.value == IssueContentDisplayMode.Section) {
                        issueContentViewModel.setDisplayable(
                            IssueKeyWithDisplayableKey(
                                issueKey,
                                displayable.key
                            )
                        )
                    }
                }
            }
            hideLogoIfNecessary(position)
            lastPage = position
        }
    }

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        super.onDestroyView()
    }

    override fun onStart() {
        viewBinding.webviewPagerViewpager.registerOnPageChangeCallback(pageChangeListener)
        super.onStart()
    }

    override fun onStop() {
        viewBinding.webviewPagerViewpager.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewBinding.navigationBottomWebviewPager.visibility = View.VISIBLE
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottomWebviewPager,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
        )
    }

    private inner class SectionPagerAdapter(val sectionStubs: List<SectionStub>) :
        FragmentStateAdapter(this@SectionPagerFragment) {

        override fun createFragment(position: Int): Fragment {
            val isImprint = position == sectionStubs.size
            val fragment = if (isImprint) {
                ImprintWebViewFragment()
            } else {
                val sectionStub = sectionStubs[position]
                val isFirstSection = position == 0
                SectionWebViewFragment.newInstance(sectionStub.sectionFileName, isFirstSection)
            }
            return fragment
        }

        // The imprint is added in addition to the sectionStubs - thus we have to add + 1
        override fun getItemCount(): Int = sectionStubs.size + 1
    }

    private fun tryScrollToSection() {
        val displayableKey = issueContentViewModel.displayableKeyLiveData.value
        if (displayableKey?.startsWith("sec") == true) {
            log.debug("Section selected: $displayableKey")
            issueContentViewModel.lastSectionKey = displayableKey

            getSupposedPagerPosition()?.let {
                if (it >= 0 && it != getCurrentPagerPosition()) {
                    viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                    hideLogoIfNecessary(it)
                }
            }
            issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Section)
        }
    }

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position =
            (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.indexOfFirst {
                it.key == issueContentViewModel.displayableKeyLiveData.value
            }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    /**
     * On advertisements we hide the drawer logo.
     */
    private fun hideLogoIfNecessary(position: Int) {
        val sectionStub = (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.get(position)
        val isAdvertisement = sectionStub?.type == SectionType.advertisement

        if (isAdvertisement) {
            hideDrawerLogoWithDelay()
            sectionDrawerViewModel.onAdvertisement = true
        } else {
            showDrawerLogo()
            sectionDrawerViewModel.onAdvertisement = false
        }
    }


    private fun hideDrawerLogoWithDelay() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            val drawerLogoWidth = it.width
            activity?.findViewById<CardView>(R.id.drawer_logo_wrapper)?.let { drawerLogoWrapper ->
                activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.let { drawerLayout ->
                    val transX =
                        -drawerLogoWidth + LOGO_PEAK * resources.displayMetrics.density
                    drawerLogoWrapper.animate()
                        .withEndAction {
                            // add additional area where clicks are handled to open the drawer
                            val widthWhereToHandleLogoClick =
                                (LOGO_PEAK + LOGO_PEAK_CLICK_PADDING) * resources.displayMetrics.density
                            drawerLayout.updateDrawerLogoBoundingBox(
                                width = widthWhereToHandleLogoClick.toInt(),
                                height = drawerLogoWrapper.height
                            )
                        }
                        .setDuration(LOGO_ANIMATION_DURATION_MS)
                        .setStartDelay(HIDE_LOGO_DELAY_MS)
                        .translationX(transX)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                }
            }
        }
    }

    private fun showDrawerLogo() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            val drawerLogoWidth = it.width
            activity?.findViewById<CardView>(R.id.drawer_logo_wrapper)?.let { drawerLogoWrapper ->
                activity?.findViewById<DrawerLayout>(R.id.drawer_layout)?.let { drawerLayout ->
                    drawerLogoWrapper.animate()
                        .withEndAction {
                            drawerLayout.updateDrawerLogoBoundingBox(
                                drawerLogoWidth,
                                drawerLogoWrapper.height
                            )
                        }
                        .setDuration(LOGO_ANIMATION_DURATION_MS)
                        .setStartDelay(0L)
                        .translationX(resources.getDimension(R.dimen.drawer_logo_translation_x))
                        .setInterpolator(AccelerateDecelerateInterpolator())
                }
            }
        }
    }
}
