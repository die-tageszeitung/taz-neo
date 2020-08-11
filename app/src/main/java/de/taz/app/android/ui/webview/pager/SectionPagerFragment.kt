package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.android.synthetic.main.fragment_webview_pager.loading_screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val POSITION = "position"

class SectionPagerFragment : BaseViewModelFragment<SectionPagerViewModel>(
    R.layout.fragment_webview_pager
) {
    override val enableSideBar: Boolean = true

    private var sectionAdapter: SectionPagerAdapter? = null

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_section
    private val issueContentViewModel: IssueContentViewModel? by lazy { (parentFragment as? IssueContentFragment)?.viewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            viewModel.currentPositionLiveData.value = getInt(POSITION, 0)
        }

        issueContentViewModel?.issueOperationsLiveData?.observe(this, object : Observer<IssueOperations?> {
            override fun onChanged(t: IssueOperations?) {
                t?.let {
                    issueContentViewModel?.issueOperationsLiveData?.removeObserver(this)
                    updateAndDownloadIssue(t)
                }
            }
        })
    }

    /**
     * start download of issue - if the issue is not downloaded yet check whether the metadata has
     * changed - if yes persist and start download for updated issue
     */
    private fun updateAndDownloadIssue(issueOperations: IssueOperations) =
        CoroutineScope(Dispatchers.IO).launch {
            IssueRepository.getInstance(context?.applicationContext)
                .getIssue(issueOperations)?.let {
                    DownloadService.getInstance(context?.applicationContext).download(it)
                }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }

        viewModel.currentPositionLiveData.observeDistinct(this) {
            if (webview_pager_viewpager.currentItem != it) {
                webview_pager_viewpager.setCurrentItem(it, false)
            }
        }

        runIfNotNull(
            issueContentViewModel?.sectionList,
            viewModel.currentPosition
        ) { _, currentPosition ->
            webview_pager_viewpager.apply {
                adapter?.notifyDataSetChanged()
                setCurrentItem(currentPosition, false)
            }
            loading_screen?.visibility = View.GONE
        }

        issueContentViewModel?.issueOperationsLiveData?.observeDistinct(this)
        { issueOperations ->
            issueOperations?.let { setDrawerIssue(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }

    fun tryLoadSection(sectionFileName: String) {
        lifecycleScope.launchWhenResumed {
            issueContentViewModel?.sectionList?.indexOfFirst { it.key == sectionFileName }?.let {
                if (it >= 0) {
                    if (viewModel.currentPosition != it) {
                        lifecycleScope.launchWhenResumed {
                            webview_pager_viewpager.setCurrentItem(it, false)
                        }
                    }
                }
            }
        }
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
                lifecycleScope.launchWhenResumed {
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
            get() = issueContentViewModel?.sectionList ?: emptyList()

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
        viewModel.currentPosition?.let {
            outState.putInt(POSITION, it)
        }
        super.onSaveInstanceState(outState)
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
}