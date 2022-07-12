package de.taz.app.android.ui.home.page.coverflow

import android.content.Context
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.factor.bouncy.BouncyRecyclerView
import de.taz.app.android.util.Log

class CoverFlowLinearLayoutManager(
    context: Context,
    private val bouncyRecyclerView: BouncyRecyclerView
) : LinearLayoutManager(context, HORIZONTAL, false) {

    private val log by Log

    override fun getPaddingLeft(): Int = getPadding()

    override fun getPaddingRight(): Int = getPadding()

    private var _padding: Int = 0
    private fun getPadding(): Int {
        if (_padding == 0) {
            _padding = calculatePadding()
        }
        return _padding
    }

    /**
     * We need to create a padding to allow this recyclerview to snap also the first and the last item
     * in the middle of the screen. Without it the first item would stick to the left side of the screen
     */
    private fun calculatePadding() = bouncyRecyclerView.children.firstOrNull()?.let {
        if (it.measuredWidth > 0) {
            return bouncyRecyclerView.width / 2 - it.measuredWidth / 2
        } else 0
    } ?: 0

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        ZoomPageTransformer.adjustViewSizes(bouncyRecyclerView)
    }

}
