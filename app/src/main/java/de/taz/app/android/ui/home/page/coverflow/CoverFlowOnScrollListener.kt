package de.taz.app.android.ui.home.page.coverflow

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import kotlin.math.abs

class CoverFlowOnScrollListener(
    private val fragment: CoverflowFragment,
    private val snapHelper: GravitySnapHelper,
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
        ZoomPageTransformer.adjustViewSizes(recyclerView)

        // set alpha of date
        fragment.setTextAlpha(calculateDateTextAlpha(recyclerView))

        // set correct home icon
        setHomeIcon(snapHelper.currentSnappedPosition)

        // only if a user scroll
        if (dx != 0 || dy != 0) {
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
        val position = snapHelper.currentSnappedPosition
        val adapter = (recyclerView.adapter as? IssueFeedAdapter)
        if (position != RecyclerView.NO_POSITION && adapter != null) {
            val item = adapter.getItem(position)
            item?.let { fragment.skipToDate(it.date) }
        }
    }

    private fun setHomeIcon(position: Int) {
        fragment.getHomeFragment().apply {
            setHomeIconFilled(filled = position == 0)
        }
    }
}
