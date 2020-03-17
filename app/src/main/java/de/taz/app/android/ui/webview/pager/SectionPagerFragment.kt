package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.ViewModelBaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*

class SectionPagerFragment :
    ViewModelBaseMainFragment(R.layout.fragment_webview_pager), BackFragment {

    lateinit var viewModel: SectionPagerViewModel

    private var sectionAdapter: SectionPagerAdapter? = null

    private var sectionKey: String? = null
    private var issueFeedName: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    companion object {
        fun createInstance(initialSection: Section): SectionPagerFragment {
            val fragment = SectionPagerFragment()
            fragment.sectionKey = initialSection.sectionFileName
            return fragment
        }

        fun createInstance(issueStub: IssueStub): SectionPagerFragment {
            val fragment = SectionPagerFragment()
            fragment.issueFeedName = issueStub.feedName
            fragment.issueDate = issueStub.date
            fragment.issueStatus = issueStub.status
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this).get(SectionPagerViewModel::class.java)
        sectionKey?.let {
            viewModel.sectionKey = it
        }
        runIfNotNull(issueFeedName, issueDate, issueStatus) { feedName, date, status ->
            viewModel.apply {
                issueFeedName = feedName
                issueDate = date
                issueStatus = status
            }

        }

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
        sectionAdapter = sectionAdapter ?: SectionPagerAdapter(this)

        setupViewPager()

        viewModel.currentPositionLiveData.observeDistinct(this) {
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        viewModel.issueLiveData.observeDistinct(this) { issue ->
            runIfNotNull(issue, viewModel.currentPosition) { issue, currentPosition ->
                setSections(issue.sectionList, currentPosition)
            }
        }
    }

    private fun getCurrentFragment(): SectionWebViewFragment? {
        return childFragmentManager.fragments.firstOrNull {
            (it as? SectionWebViewFragment)?.let { fragment ->
                return@firstOrNull fragment.viewModel.displayableKey == sectionAdapter?.getCurrentSection()?.sectionFileName
            }
            return@firstOrNull false
        } as? SectionWebViewFragment
    }

    fun tryLoadSection(section: Section): Boolean {
        viewModel.sectionKey = section.sectionFileName
        return true
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            adapter = sectionAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private fun tearDownViewPager() {
        webview_pager_viewpager?.apply {
            adapter = null
        }
    }

    override fun onDestroy() {
        tearDownViewPager()
        super.onDestroy()
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.currentPositionLiveData.postValue(position)
        }
    }

    fun setSections(sections: List<Section>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as SectionPagerAdapter?)?.submitList(sections)
            setCurrentItem(currentPosition, false)
        }
    }

    fun setCurrentPosition(position: Int) {
        webview_pager_viewpager.setCurrentItem(position, false)
    }

    override fun onStop() {
        webview_pager_viewpager?.adapter = null
        super.onStop()
    }

    private inner class SectionPagerAdapter(
        fragment: Fragment
    ) : FragmentStateAdapter(fragment) {
        private var sections = emptyList<Section>()

        override fun createFragment(position: Int): Fragment {
            val section = sections[position]
            return SectionWebViewFragment.createInstance(section.sectionFileName)
        }

        override fun getItemCount(): Int = sections.size

        fun submitList(newSections: List<Section>) {
            sections = newSections
            notifyDataSetChanged()
        }

        fun getCurrentSection(): Section {
            return sections[webview_pager_viewpager.currentItem]
        }
    }

    override fun onBackPressed(): Boolean {
        (activity as? MainActivity)?.showHome()
        return true
    }
}