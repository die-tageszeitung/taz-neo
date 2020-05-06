package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.ViewModelBaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"
const val SECTION_KEY = "sectionKey"

class SectionPagerFragment :
    ViewModelBaseMainFragment(R.layout.fragment_webview_pager), BackFragment {

    private val log by Log

    val viewModel = SectionPagerViewModel()

    private var sectionAdapter: SectionPagerAdapter? = null

    private var sectionKey: String? = null
    private var issueFeedName: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    companion object {
        fun createInstance(sectionFileName: String): SectionPagerFragment {
            val fragment = SectionPagerFragment()
            fragment.sectionKey = sectionFileName
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            issueDate = getString(ISSUE_DATE)
            issueFeedName = getString(ISSUE_FEED)
            try {
                issueStatus = getString(ISSUE_STATUS)?.let { IssueStatus.valueOf(it) }
            } catch (e: IllegalArgumentException) {
                // do nothing issueStatus is null
            }
            sectionKey = getString(SECTION_KEY)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sectionKey?.let {
            viewModel.sectionKey = it
            sectionKey = null
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

        viewModel.currentPositionLiveData.observeDistinct(this) {
            log.debug("currerntPostiion changed to $it")
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        viewModel.sectionStubListLiveData.observeDistinct(this) { sectionStubList ->
            runIfNotNull(
                sectionStubList,
                viewModel.currentPosition
            ) { sectionStubs, currentPosition ->
                setSections(sectionStubs, currentPosition)
                loading_screen?.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        setupViewPager()

        viewModel.currentPosition?.let {
            if (it != webview_pager_viewpager.currentItem) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        super.onStart()
    }

    fun tryLoadSection(sectionFileName: String): Boolean {
        lifecycleScope.launch(Dispatchers.IO) {
            val sectionStubs =
                SectionRepository.getInstance().getAllSectionStubsForSectionName(sectionFileName)

            withContext(Dispatchers.Main) {
                webview_pager_viewpager.setCurrentItem(
                    sectionStubs.indexOfFirst { it.key == sectionFileName }, false
                )
            }
        }
        return true
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            adapter = sectionAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            registerOnPageChangeCallback(pageChangeListener)
            offscreenPageLimit = 2
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.currentPosition = position
            getMainView()?.setActiveDrawerSection(position)
            sectionAdapter?.getSectionStub(position)?.let {
                lifecycleScope.launch {
                    val navButton = it.getNavButton()
                    showNavButton(navButton)
                }
            }
        }
    }

    private fun setSections(sections: List<SectionStub>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as SectionPagerAdapter?)?.submitList(sections)
            setCurrentItem(currentPosition, false)
        }
    }

    override fun onStop() {
        webview_pager_viewpager?.adapter = null
        webview_pager_viewpager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    private inner class SectionPagerAdapter(
        fragment: Fragment
    ) : FragmentStateAdapter(fragment) {

        private var sectionStubs = emptyList<SectionStub>()

        override fun createFragment(position: Int): Fragment {
            val section = sectionStubs[position]
            return SectionWebViewFragment.createInstance(section)
        }

        override fun getItemCount(): Int = sectionStubs.size

        fun submitList(newSections: List<SectionStub>) {
            if (sectionStubs != newSections) {
                sectionStubs = newSections
                notifyDataSetChanged()
            }
        }

        fun getSectionStub(position: Int): SectionStub {
            return sectionStubs[position]
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_DATE, issueDate)
        outState.putString(ISSUE_FEED, issueFeedName)
        outState.putString(ISSUE_STATUS, issueStatus.toString())
        outState.putString(SECTION_KEY, sectionKey)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed(): Boolean {
        (activity as? MainActivity)?.showHome()
        return true
    }
}