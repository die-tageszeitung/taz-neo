package de.taz.app.android.ui.home.page.coverflow


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.home.page.HomePageListAdapter
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.ui.main.MainDataController
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class CoverflowFragment : BaseMainFragment<CoverflowContract.Presenter>(), CoverflowContract.View {

    override val presenter = CoverflowPresenter()

    val log by Log
    private val issueRepository = IssueRepository.getInstance()

    private val coverFlowPagerAdapter = HomePageListAdapter(
        this@CoverflowFragment,
        R.layout.fragment_cover_flow_item,
        presenter
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coverflow, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        coverflow_pager.apply {
            setPageTransformer(ZoomPageTransformer())
            overScrollMode = View.OVER_SCROLL_NEVER
            offscreenPageLimit = 3
            adapter = coverFlowPagerAdapter
            registerOnPageChangeCallback(CoverFlowOnPageChangeCallback())
        }
        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        val oldItemCount = coverFlowPagerAdapter.itemCount
        coverFlowPagerAdapter.apply {
            setIssueStubs(issueStubs.reversed())
        }
        if (oldItemCount == 0) {
            skipToEnd()
        }
    }

    override fun setFeeds(feeds: List<Feed>) {
        coverFlowPagerAdapter.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        val oldItemCount = coverFlowPagerAdapter.itemCount
        coverFlowPagerAdapter.setInactiveFeedNames(inactiveFeedNames)
        if (oldItemCount == 0) {
            skipToEnd()
        }
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

    override fun skipToEnd() {
        coverflow_pager.adapter?.let {
            coverflow_pager.currentItem = it.itemCount - 1
        }
    }

    inner class CoverFlowOnPageChangeCallback : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                informSideBarOfIssueChange(position)

                val visibleItemCount = 3
                val totalItemCount = coverFlowPagerAdapter.itemCount

                if (position > totalItemCount - 2 * visibleItemCount) {
                    coverFlowPagerAdapter.getItem(0)?.date?.let { requestDate ->
                        presenter.getNextIssueMoments(requestDate)
                    }
                }
            }

        }

        @UiThread
        private fun informSideBarOfIssueChange(position: Int) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val viewModel = ViewModelProviders.of(requireActivity()).get(
                    MainDataController::class.java
                )
                coverFlowPagerAdapter.getItem(position)?.let { issueStub ->
                    issueRepository.getIssue(issueStub).let { issue ->
                        viewModel.setIssue(issue)
                    }
                }
            }

        }
    }
}