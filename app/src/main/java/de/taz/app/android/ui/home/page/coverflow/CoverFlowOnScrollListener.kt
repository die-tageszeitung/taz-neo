package de.taz.app.android.ui.home.page.coverflow

import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import kotlin.math.abs

class CoverFlowOnScrollListener(
    private val fragment: CoverflowFragment,
    private val snapHelper: SnapHelper,
) : RecyclerView.OnScrollListener() {

    private var isDragEvent = false

    override fun onScrollStateChanged(
        recyclerView: RecyclerView,
        newState: Int
    ) {
        super.onScrollStateChanged(recyclerView, newState)

        // if user is dragging to left if no newer issue -> refresh
        if (isDragEvent && !recyclerView.canScrollHorizontally(-1)) {
            fragment.getHomeFragment().refresh()
        }

        // set possible Event states
        isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        adjustViewSizes(recyclerView)

        // only apply these changes if a user scroll
        if (dx != 0 || dy != 0) {
            // set alpha of date
            fragment.setTextAlpha(calculateDateTextAlpha(recyclerView))
            // set new date
            updateCurrentDate(recyclerView)
        }
    }

    private fun calculateDateTextAlpha(recyclerView: RecyclerView): Float {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager

        val view = snapHelper.findSnapView(layoutManager)
        val orientationHelper = OrientationHelper.createHorizontalHelper(layoutManager)

        val currentViewDistance = abs(
            orientationHelper.startAfterPadding - orientationHelper.getDecoratedStart(view)
        )
        return 1 - (currentViewDistance.toFloat() * 2 / orientationHelper.totalSpace)
    }

    private fun updateCurrentDate(recyclerView: RecyclerView) {
        snapHelper.findSnapView(recyclerView.layoutManager)?.let {
            val position = recyclerView.getChildAdapterPosition(it)
            setSelectedDateByPosition(recyclerView, position)
        }
    }

    private fun setSelectedDateByPosition(recyclerView: RecyclerView, position: Int) {
        fragment.getHomeFragment().apply {
            if (position == 0) {
                setHomeIconFilled()
            } else {
                setHomeIcon()
            }
        }
        (recyclerView.adapter as IssueFeedAdapter).getItem(position)?.let { date ->
            fragment.skipToDate(date)
        }
    }

    private fun adjustViewSizes(recyclerView: RecyclerView) {
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
