package de.taz.app.android.ui.home.page.coverflow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

const val KEY_CURRENT_DATE = "KEY_CURRENT_DATE"
const val KEY_ALPHA="KEVIN"

class CoverFlowOnScrollListener(
    private val viewModel: ViewModel,
    private val snapHelper: GravitySnapHelper,
) : RecyclerView.OnScrollListener() {

    class ViewModel(
        application: Application,
        savedStateHandle: SavedStateHandle
    ) : AndroidViewModel(application) {
        val refresh = MutableSharedFlow<Unit>(0)
        val dateAlpha = savedStateHandle.getMutableStateFlow(KEY_ALPHA, 1f)
        val currentDate = savedStateHandle.getMutableStateFlow<Date>(KEY_CURRENT_DATE, Date())
    }

    private var isDragEvent = false

    override fun onScrollStateChanged(
        recyclerView: RecyclerView,
        newState: Int
    ) {
        super.onScrollStateChanged(recyclerView, newState)

        // if user is dragging to left if no newer issue -> refresh
        if (isDragEvent && !recyclerView.canScrollHorizontally(-1)) {
            viewModel.viewModelScope.launch {
                viewModel.refresh.emit(Unit)
            }
        }

        // set possible Event states
        isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        ZoomPageTransformer.adjustViewSizes(recyclerView)

        // set alpha of date
        viewModel.viewModelScope.launch {
            viewModel.dateAlpha.emit(calculateDateTextAlpha(recyclerView))
        }

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
            viewModel.viewModelScope.launch {
                item?.let { viewModel.currentDate.emit(it.date) }
            }
        }
    }
}
