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
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"

class CoverflowFragment : HomePageFragment(R.layout.fragment_coverflow) {

    val log by Log

    private val openDatePicker: (Date) -> Unit = { issueDate ->
        showBottomSheet(DatePickerFragment.create(this, issueDate))
    }
    var coverflowAdapter: CoverflowAdapter? = null

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null
    private var issueFeedname: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        coverflowAdapter = coverflowAdapter ?: CoverflowAdapter(
            this,
            R.layout.fragment_cover_flow_item,
            openDatePicker
        )

        savedInstanceState?.apply {
            runIfNotNull(
                getString(ISSUE_DATE),
                getString(ISSUE_FEED),
                getString(ISSUE_STATUS)
            ) { date, feed, status ->
                issueDate = date
                issueFeedname = feed
                issueStatus = IssueStatus.valueOf(status)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragment_cover_flow_grid.apply {
            context?.let { context ->
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            adapter = coverflowAdapter

            snapHelper.apply {
                attachToRecyclerView(fragment_cover_flow_grid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true
            }
        }

        fragment_cover_flow_to_archive.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem += 1
            }
        }
    }

    override fun onResume() {
        fragment_cover_flow_grid.addOnScrollListener(onScrollListener)
        skipToCurrentItem()
        applyZoomPageTransformer()
        getMainView()?.apply {
            setDefaultDrawerNavButton()
            setActiveDrawerSection(RecyclerView.NO_POSITION)
            changeDrawerIssue()
        }
        super.onResume()
    }

    override fun onPause() {
        fragment_cover_flow_grid.apply {
            removeOnScrollListener(onScrollListener)
        }
        super.onPause()
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        coverflowAdapter?.setIssueStubs(issueStubs)
    }

    override fun setAuthStatus(authStatus: AuthStatus) {
        coverflowAdapter?.setAuthStatus(authStatus)
    }

    override fun setFeeds(feeds: List<Feed>) {
        coverflowAdapter?.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(feedNames: Set<String>) {
        coverflowAdapter?.setInactiveFeedNames(feedNames)
    }

    fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    fun skipToHome() {
        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
            setCurrentItem(coverflowAdapter?.getItem(0))
            fragment_cover_flow_grid.layoutManager?.scrollToPosition(0)
            snapHelper.scrollToPosition(0)
            (parentFragment as? HomeFragment)?.setHomeIconFilled()
        }
    }

    fun skipToCurrentItem(): Boolean {
        return runIfNotNull(issueFeedname, issueDate, issueStatus) { feed, date, status ->
            val position = coverflowAdapter?.getPosition(feed, date, status) ?: -1
            val layoutManager = fragment_cover_flow_grid.layoutManager as? LinearLayoutManager
            layoutManager?.apply {
                val currentPosition: Int =
                    (findFirstVisibleItemPosition() + findLastVisibleItemPosition()) / 2
                if (position > 0) {
                    if (position != currentPosition) {
                        fragment_cover_flow_grid.layoutManager?.scrollToPosition(position)
                    }
                    return@runIfNotNull true
                } else if (position == 0) {
                    skipToHome()
                    return@runIfNotNull true
                }
            }
            return@runIfNotNull false
        } ?: false
    }

    fun skipToItem(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        this.issueFeedname = issueFeedName
        this.issueDate = issueDate
        this.issueStatus = issueStatus
        skipToCurrentItem()
    }

    fun skipToItem(issueStub: IssueOperations) =
        skipToItem(issueStub.feedName, issueStub.date, issueStub.status)

    inner class OnScrollListener : RecyclerView.OnScrollListener() {

        private var isDragEvent = false
        private var isIdleEvent = false

        override fun onScrollStateChanged(
            recyclerView: RecyclerView,
            newState: Int
        ) {
            // if user is dragging to left if no newer issue -> refresh
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_SETTLING && isDragEvent &&
                !recyclerView.canScrollHorizontally(-1)
            ) {
                activity?.findViewById<SwipeRefreshLayout>(R.id.coverflow_refresh_layout)
                    ?.setRefreshingWithCallback(true)
            }
            isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
            isIdleEvent = newState == RecyclerView.SCROLL_STATE_IDLE
        }


        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val position: Int =
                (layoutManager.findFirstVisibleItemPosition() + layoutManager.findLastVisibleItemPosition()) / 2

            // snap first time the position is set by the fragment
            if (dx == 0 && dy == 0 && !isDragEvent) {
                snapHelper.scrollToPosition(position)
            }

            // transform the visible children visually
            applyZoomPageTransformer()

            // persist position and download new issues if user is scrolling
            if (position >= 0 && !isIdleEvent) {
                (parentFragment as? HomeFragment)?.apply {
                    if (position == 0) {
                        setHomeIconFilled()
                    } else {
                        setHomeIcon()
                    }
                }

                setCurrentItem(coverflowAdapter?.getItem(position))
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val visibleItemCount = 5
                    coverflowAdapter?.let { coverflowAdapter ->
                        if (position > coverflowAdapter.itemCount - visibleItemCount) {
                            coverflowAdapter.getItem(coverflowAdapter.itemCount - 1)?.date?.let { requestDate ->
                                getNextIssueMoments(requestDate)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setCurrentItem(issueOperations: IssueOperations?) {
        issueOperations?.let {
            issueFeedname = issueOperations.feedName
            issueStatus = issueOperations.status
            issueDate = issueOperations.date
        }
    }

    fun hasSetItem(): Boolean {
        return runIfNotNull(issueFeedname, issueDate, issueStatus) { _, _, _ ->
            true
        } ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_FEED, issueFeedname)
        outState.putString(ISSUE_STATUS, issueStatus?.toString())
        outState.putString(ISSUE_DATE, issueDate)
        super.onSaveInstanceState(outState)
    }

    private fun applyZoomPageTransformer() {
        (fragment_cover_flow_grid as? ViewGroup)?.apply {
            children.forEach { child ->
                val childPosition = (child.left + child.right) / 2f
                val center = width / 2

                ZoomPageTransformer.transformPage(child, (center - childPosition) / width)
            }
        }
    }

    override fun callbackWhenIssueIsSet() {
        applyZoomPageTransformer()
    }

    override fun onDestroyView() {
        snapHelper.attachToRecyclerView(null)
        fragment_cover_flow_grid.adapter = null
        super.onDestroyView()
    }

}
