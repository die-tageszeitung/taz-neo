package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen
const val POSITION = "position"

class SectionPagerFragment : BaseMainFragment(
    R.layout.fragment_webview_pager
) {
    private val log by Log

    override val enableSideBar: Boolean = true

    private var sectionPagerAdapter: SectionPagerAdapter? = null

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_section

    private val issueContentViewModel: IssueContentViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueContentViewModel::class.java)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
        loading_screen?.visibility = View.GONE


        issueContentViewModel.sectionListLiveData.observeDistinct(this.viewLifecycleOwner) { sectionStubs ->
            (webview_pager_viewpager.adapter as? SectionPagerAdapter)?.let { viewPagerAdapter ->
                viewPagerAdapter.sectionStubs = sectionStubs
                // after receiving a new article list scroll to current displayKey if available
                tryScrollToSection()
            }
        }

        issueContentViewModel.issueStubAndDisplayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) { (_, _) ->
            tryScrollToSection()
        }
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            sectionPagerAdapter = SectionPagerAdapter()
            adapter = sectionPagerAdapter

            adapter?.notifyDataSetChanged()
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            registerOnPageChangeCallback(pageChangeListener)
            offscreenPageLimit = 2
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        override fun onPageSelected(position: Int) {
            if (lastPage != null && lastPage != position) {
                runIfNotNull(
                    issueContentViewModel.issueStubAndDisplayableKeyLiveData.value?.first,
                    issueContentViewModel.sectionListLiveData.value?.getOrNull(position)
                ) { issueStub, displayable ->
                    issueContentViewModel.setDisplayable(issueStub.issueKey, displayable.key)
                }
            }
            lastPage = position


            lifecycleScope.launchWhenResumed {
                getCurrentSectionStub()?.getNavButton()?.let {
                    showNavButton(it)
                }
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome(skipToNewestIssue = true)
            }
            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }

    override fun onStop() {
        webview_pager_viewpager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    private inner class SectionPagerAdapter:
        FragmentStateAdapter(this@SectionPagerFragment) {
        var sectionStubs: List<SectionStub> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun createFragment(position: Int): Fragment {
            val sectionStub = sectionStubs[position]
            return SectionWebViewFragment.createInstance(sectionStub.sectionFileName)
        }

        override fun getItemCount(): Int = sectionStubs.size
    }

    private fun tryScrollToSection() {
        if (issueContentViewModel.displayableKeyLiveData.value?.startsWith("sec") == true) {
            issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Section)
            lifecycleScope.launchWhenStarted {
                getCurrentPagerPosition()?.let {
                    if (it >= 0) {
                        webview_pager_viewpager.setCurrentItem(it, false)
                    }
                }
            }
        }
    }

    private fun getCurrentPagerPosition(): Int? {
        val position = issueContentViewModel.sectionListLiveData.value?.indexOfFirst {
            it.key == issueContentViewModel.displayableKeyLiveData.value
        }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentSectionStub(): SectionStub? {
        return getCurrentPagerPosition()?.let {
            issueContentViewModel.sectionListLiveData.value?.get(it)
        }
    }
}