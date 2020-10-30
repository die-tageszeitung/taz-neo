package de.taz.app.android.ui.home.page.coverflow


import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedPagingAdapter
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import java.util.*

const val ISSUE_KEY = "ISSUE_KEY"

class CoverflowFragment : HomePageFragment(R.layout.fragment_coverflow) {

    val log by Log

    private val openDatePicker: (Date) -> Unit = { issueDate ->
        showBottomSheet(DatePickerFragment.create(this, issueDate))
    }
    override lateinit var adapter: IssueFeedPagingAdapter

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private var issueKey: IssueKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = CoverflowAdapter(
            this@CoverflowFragment,
            R.layout.fragment_cover_flow_item,
            openDatePicker
        )
        savedInstanceState?.apply {
            issueKey = getParcelable(ISSUE_KEY)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragment_cover_flow_grid.apply {
            context?.let { context ->
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            adapter = this@CoverflowFragment.adapter

            // transform the visible children visually
            snapHelper.apply {
                attachToRecyclerView(fragment_cover_flow_grid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true
            }

        }

        fragment_cover_flow_grid.addOnChildAttachStateChangeListener(object: RecyclerView.OnChildAttachStateChangeListener  {
            override fun onChildViewAttachedToWindow(view: View) {
                applyZoomPageTransformer()
            }
            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })

        fragment_cover_flow_to_archive.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem += 1
            }
        }
    }

    override fun onResume() {
        fragment_cover_flow_grid.addOnScrollListener(onScrollListener)
        //skipToCurrentItem()
        super.onResume()
    }

    override fun onPause() {
        fragment_cover_flow_grid.apply {
            removeOnScrollListener(onScrollListener)
        }
        super.onPause()
    }

    /*
        fun skipToHome() {
            viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                setCurrentItem(adapter?.peek(0)?.issueStub)
                fragment_cover_flow_grid.layoutManager?.scrollToPosition(0)
                snapHelper.scrollToPosition(0)
                (parentFragment as? HomeFragment)?.setHomeIconFilled()
            }
        }

        fun skipToCurrentItem(): Boolean {
            return issueKey?.let {
                val position = adapter?.getPosition(it) ?: -1
                val layoutManager = fragment_cover_flow_grid.layoutManager as? LinearLayoutManager
                layoutManager?.apply {
                    val currentPosition: Int =
                        (findFirstVisibleItemPosition() + findLastVisibleItemPosition()) / 2
                    if (position > 0) {
                        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                            if (position != currentPosition) {
                                setCurrentItem(adapter?.peek(position)?.issueStub)
                                fragment_cover_flow_grid.layoutManager?.scrollToPosition(position)
                                snapHelper.scrollToPosition(position)
                            }
                        }
                        return true
                    } else if (position == 0) {
                        skipToHome()
                        return true
                    }
                }
                return false
            } ?: false
        }

        private fun skipToItem(issueKey: IssueKey) {
            this.issueKey = issueKey
            skipToCurrentItem()
        }

        fun skipToItem(issueStub: IssueOperations) =
            skipToItem(issueStub.issueKey)
    */
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

                setCurrentItem(adapter?.peek(position)?.issueStub)
            }
        }
    }

    private fun setCurrentItem(issueOperations: IssueOperations?) {
        issueKey = issueOperations?.issueKey
    }

    fun hasSetItem(): Boolean {
        return issueKey != null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ISSUE_KEY, issueKey)
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
