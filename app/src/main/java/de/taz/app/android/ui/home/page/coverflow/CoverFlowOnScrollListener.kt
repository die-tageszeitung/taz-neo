package de.taz.app.android.ui.home.page.coverflow

import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import de.taz.app.android.R
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import kotlin.math.abs

class CoverFlowOnScrollListener(
    private val fragment: CoverflowFragment,
    private val snapHelper: SnapHelper,
) : RecyclerView.OnScrollListener() {

    private var isDragEvent = false
    private var isIdleEvent = false
    private var isSettlingEvent = false

    private val activity
        get() = fragment.requireActivity()

    override fun onScrollStateChanged(
        recyclerView: RecyclerView,
        newState: Int
    ) {
        super.onScrollStateChanged(recyclerView, newState)

        // if user is dragging to left if no newer issue -> refresh
        if (isDragEvent && !recyclerView.canScrollHorizontally(-1)) {
            activity.findViewById<SwipeRefreshLayout>(R.id.coverflow_refresh_layout)
                ?.setRefreshingWithCallback(true)
        }
        isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
        isIdleEvent = newState == RecyclerView.SCROLL_STATE_IDLE
        isSettlingEvent = newState == RecyclerView.SCROLL_STATE_SETTLING

    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val orientationHelper = OrientationHelper.createHorizontalHelper(layoutManager)

        snapHelper.findSnapView(layoutManager)?.let {
            val currentViewDistance = abs(
                orientationHelper.startAfterPadding
                        - orientationHelper.getDecoratedStart(it)
            )
            val alphaPercentage = 1 -
                    (currentViewDistance.toFloat() * 2 / orientationHelper.totalSpace)

            fragment.setTextAlpha(alphaPercentage)

            val position = recyclerView.getChildAdapterPosition(it)
            setSelectedDateByPosition(recyclerView, position)
        }
        applyZoomPageTransformer(recyclerView)
    }

    private fun setSelectedDateByPosition(recyclerView: RecyclerView, position: Int) {
        (fragment.parentFragment as? HomeFragment)?.apply {
            if (position == 0) {
                setHomeIconFilled()
            } else {
                setHomeIcon()
            }
        }
        (recyclerView.adapter as IssueFeedAdapter).getItem(position)?.let { date ->
           fragment.skipToDate(date, scroll = false)
        }
    }

    private fun applyZoomPageTransformer(recyclerView: RecyclerView) {
        recyclerView.apply {
            children.forEach { child ->
                val childPosition = (child.left + child.right) / 2f
                val center = width / 2
                if (childPosition != 0f) {
                    ZoomPageTransformer.transformPage(child, (center - childPosition) / width)
                }
            }
        }
    }
}
