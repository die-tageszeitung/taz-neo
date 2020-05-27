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
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"
const val POSITION = "position"
const val SECTION_KEY = "sectionKey"

class SectionPagerFragment :
    ViewModelBaseMainFragment(R.layout.fragment_webview_pager), BackFragment {

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
            viewModel.currentPositionLiveData.value = getInt(POSITION, 0)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sectionKey?.let {
            viewModel.sectionKeyLiveData.value = it
        }
        runIfNotNull(issueFeedName, issueDate, issueStatus) { feedName, date, status ->
            viewModel.apply {
                issueFeedNameLiveData.value = feedName
                issueDateLiveData.value = date
                issueStatusLiveData.value = status
            }
        }

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        viewModel.currentPositionLiveData.observeDistinct(this) {
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        viewModel.sectionStubListLiveData.observeDistinct(this) { sectionStubList ->
            runIfNotNull(
                sectionStubList,
                viewModel.currentPosition
            ) { _, currentPosition ->
                webview_pager_viewpager.apply {
                    adapter?.notifyDataSetChanged()
                    setCurrentItem(currentPosition, false)
                }
                loading_screen?.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
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
            if (adapter == null) {
                sectionAdapter = SectionPagerAdapter()
                adapter = sectionAdapter
            }
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            registerOnPageChangeCallback(pageChangeListener)
            offscreenPageLimit = 2
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.currentPositionLiveData.value = position
            getMainView()?.setActiveDrawerSection(position)
            sectionAdapter?.getSectionStub(position)?.let {
                lifecycleScope.launch {
                    val navButton = it.getNavButton()
                    showNavButton(navButton)
                }
            }
        }
    }

    override fun onStop() {
        webview_pager_viewpager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    private inner class SectionPagerAdapter : FragmentStateAdapter(this@SectionPagerFragment) {

        private val sectionStubs: List<SectionStub>
            get() = viewModel.sectionStubListLiveData.value ?: emptyList()

        override fun createFragment(position: Int): Fragment {
            val section = sectionStubs[position]
            return SectionWebViewFragment.createInstance(section)
        }

        override fun getItemCount(): Int = sectionStubs.size

        fun getSectionStub(position: Int): SectionStub {
            return sectionStubs[position]
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_DATE, issueDate ?: viewModel.issueDate)
        outState.putString(ISSUE_FEED, issueFeedName ?: viewModel.issueFeedName)
        outState.putString(
            ISSUE_STATUS,
            issueStatus?.toString() ?: viewModel.issueStatus?.toString()
        )
        outState.putString(SECTION_KEY, viewModel.sectionKey)
        viewModel.currentPosition?.let {
            outState.putInt(POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed(): Boolean {
        (activity as? MainActivity)?.showHome()
        return true
    }

}