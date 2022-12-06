package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull

class SectionPagerFragment : BaseMainFragment<FragmentWebviewPagerBinding>() {
    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
        setupViewPager()

        issueContentViewModel.sectionListLiveData.observeDistinct(this.viewLifecycleOwner) { sectionStubs ->
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

        issueContentViewModel.displayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) {
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
            if (lastPage != null && lastPage != position) {
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                    (viewBinding.webviewPagerViewpager.adapter as SectionPagerAdapter).sectionStubs[position]
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
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottomWebviewPager,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
        )
    }

    private inner class SectionPagerAdapter(val sectionStubs: List<SectionStub>) :
        FragmentStateAdapter(this@SectionPagerFragment) {

        override fun createFragment(position: Int): Fragment {
            val sectionStub = sectionStubs[position]
            val isFirstSection = position == 0
            return SectionWebViewFragment.newInstance(sectionStub.sectionFileName, isFirstSection)
        }

        override fun getItemCount(): Int = sectionStubs.size
    }

    private fun tryScrollToSection() {
        val displayableKey = issueContentViewModel.displayableKeyLiveData.value
        if (displayableKey?.startsWith("sec") == true) {
            log.debug("Section selected: $displayableKey")
            issueContentViewModel.lastSectionKey = displayableKey

            getSupposedPagerPosition()?.let {
                if (it >= 0 && it != getCurrentPagerPosition()) {
                    viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
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
}