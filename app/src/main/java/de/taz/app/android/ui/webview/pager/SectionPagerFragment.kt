package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.HorizontalSectionSwipeCoachMark
import de.taz.app.android.databinding.FragmentWebviewSectionPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.SectionImprintWebViewFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch


class SectionPagerFragment : BaseMainFragment<FragmentWebviewSectionPagerBinding>() {
    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
        }
        setupViewPager()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    issueContentViewModel.sectionListFlow.collect { sectionStubs ->
                        if (
                            sectionStubs.map { it.key } !=
                            (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.map { it.key }
                        ) {
                            log.debug("New set of sections: ${sectionStubs.map { it.key }}")
                            viewBinding.webviewPagerViewpager.adapter =
                                SectionPagerAdapter(sectionStubs)
                            viewBinding.loadingScreen.root.visibility = View.GONE
                        }
                    }
                }

                launch {
                    issueContentViewModel.displayableKeyFlow.collect {
                        tryScrollToSection(it)
                    }
                }

                launch {
                    issueContentViewModel.activeDisplayModeFlow.collect {
                        // We show here the couch mark for horizontal swipe. It is at this place because
                        // the [SectionPagerFragment] is always created in the [IssueViewerActivity], even if
                        // an article is shown. But the [activeDisplayMode] gives us the indication that we
                        // are on an section.
                        if (it == IssueContentDisplayMode.Section) {
                            HorizontalSectionSwipeCoachMark(this@SectionPagerFragment)
                                .maybeShow()
                        }
                    }
                }
            }
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
            val sectionStubs =
                (viewBinding.webviewPagerViewpager.adapter as SectionPagerAdapter).sectionStubs
            // if we are beyond last position we are the imprint
            val isImprint = position == sectionStubs.size
            if (lastPage != null && lastPage != position && !isImprint) {
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyFlow.value?.issueKey,
                    sectionStubs[position]
                ) { issueKey, displayable ->
                    if (issueContentViewModel.activeDisplayModeFlow.value == IssueContentDisplayMode.Section) {
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

        // To detect whether we have swiped through the section pager manually, it is not enough to
        // listen on the `onPageSelected` as that is triggered too by clicking a section in the drawer.
        // So we need to really listen to `onPageSCrolled` to detect real horizontal swipes:
        var scrolled = false
        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            if (state == SCROLL_STATE_DRAGGING) {
                scrolled = true
            }
            if (state == SCROLL_STATE_IDLE && scrolled) {
                scrolled = false
                lifecycleScope.launch {
                    HorizontalSectionSwipeCoachMark.setFunctionAlreadyDiscovered(requireContext())
                }
            }
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
                SectionImprintWebViewFragment()
            } else {
                val sectionStub = sectionStubs[position]
                val isFirstSection = position == 0
                SectionWebViewFragment.newInstance(sectionStub, isFirstSection)
            }
            return fragment
        }

        // The imprint is added in addition to the sectionStubs - thus we have to add + 1
        override fun getItemCount(): Int = sectionStubs.size + 1
    }

    private suspend fun tryScrollToSection(displayableKey: String) {
        if (displayableKey.startsWith("sec")) {
            log.debug("Section selected: $displayableKey")
            issueContentViewModel.lastSectionKey = displayableKey

            getSupposedPagerPosition()?.let {
                if (it >= 0 && it != getCurrentPagerPosition()) {
                    viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                }
            }
        }
    }

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private suspend fun getSupposedPagerPosition(): Int? {
        val position =
            (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.indexOfFirst {
                it.key == issueContentViewModel.displayableKeyFlow.first()
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
        val sectionStub =
            (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.getOrNull(
                position
            )
        val isAdvertisement = sectionStub?.type == SectionType.advertisement
        val isPodcast = sectionStub?.type == SectionType.podcast
        if (isAdvertisement || isPodcast) {
            drawerAndLogoViewModel.hideLogo()
        } else {
            drawerAndLogoViewModel.showLogo()
        }
    }
}
