package de.taz.app.android.ui.home.page.coverflow


import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CoverflowFragment : HomePageFragment(R.layout.fragment_coverflow) {

    val log by Log

    private val openDatePicker =  {
        showBottomSheet(DatePickerFragment())
    }

    private val coverFlowPagerAdapter = CoverflowAdapter(
        this@CoverflowFragment,
        R.layout.fragment_cover_flow_item,
        null
    )
    private val snapHelper = GravitySnapHelper(Gravity.CENTER)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        fragment_cover_flow_grid.apply {
            context?.let { context ->
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            adapter = coverFlowPagerAdapter
            addOnScrollListener(CoverFlowOnPageChangeCallback())

            snapHelper.apply {
                attachToRecyclerView(fragment_cover_flow_grid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true
            }

            addOnScrollListener(object : RecyclerView.OnScrollListener() {

                private var isDragEvent = false

                override fun onScrollStateChanged(
                    recyclerView: RecyclerView,
                    newState: Int
                ) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if ( newState == RecyclerView.SCROLL_STATE_SETTLING && isDragEvent &&
                        !recyclerView.canScrollHorizontally(1)
                    ) {
                        activity?.findViewById<SwipeRefreshLayout>(R.id.coverflow_refresh_layout)
                            ?.setRefreshingWithCallback(true)
                    }
                    isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
                }
            })

        }

        fragment_cover_flow_to_archive.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem += 1
            }
        }
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        coverFlowPagerAdapter.apply {
            setIssueStubs(issueStubs.reversed())
        }
    }

    override fun setAuthStatus(authStatus: AuthStatus) {
        coverFlowPagerAdapter.setAuthStatus(authStatus)
        getCurrentPosition()?.let {
            skipToPosition(it)
        }
    }

    override fun setFeeds(feeds: List<Feed>) {
        coverFlowPagerAdapter.setFeeds(feeds)
        getCurrentPosition()?.let {
            skipToPosition(it)
        }
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        coverFlowPagerAdapter.setInactiveFeedNames(inactiveFeedNames)
        getCurrentPosition()?.let {
            skipToPosition(it)
        }
    }

    fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    fun skipToEnd() {
        fragment_cover_flow_grid.apply {
            scrollToPosition(coverFlowPagerAdapter.itemCount.minus(1))
            smoothScrollBy(1, 0)
        }
    }

    fun skipToItem(issueStub: IssueStub) {
        val position = coverFlowPagerAdapter.getPosition(issueStub)
        if (position > 0) {
            skipToPosition(position)
        }
    }

    fun skipToPosition(position: Int) {
        fragment_cover_flow_grid.apply {
            scrollToPosition(position)
            smoothScrollBy(1, 0)
        }
    }

    inner class CoverFlowOnPageChangeCallback : RecyclerView.OnScrollListener() {

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val position: Int = (
                    layoutManager.findFirstVisibleItemPosition() + layoutManager.findLastVisibleItemPosition()
                    ) / 2

            (fragment_cover_flow_grid as? ViewGroup)?.apply {
                children.forEach { child ->
                    val childPosition = (child.left + child.right) / 2f
                    val center = width / 2

                    ZoomPageTransformer.transformPage(child, (center - childPosition) / width)
                }
            }

            if (position >= 0 && position != getCurrentPosition()) {
                setCurrentPosition(position)

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

                    getMainView()?.apply {
                        coverFlowPagerAdapter.getItem(position)?.let {
                            setDrawerIssue(it)
                            if (getMainView()?.isDrawerVisible(Gravity.START) == true) {
                                changeDrawerIssue()
                            }
                        }
                    }

                    val visibleItemCount = 3

                    if (position < 2 * visibleItemCount) {
                        coverFlowPagerAdapter.getItem(0)?.date?.let { requestDate ->
                            getNextIssueMoments(requestDate)
                        }
                    }
                }
            }
        }
    }
}
