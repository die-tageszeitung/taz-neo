package de.taz.app.android.ui.home.page.coverflow

import android.content.Context
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.factor.bouncy.BouncyRecyclerView

class CoverFlowLinearLayoutManager(
    context: Context,
    private val bouncyRecyclerView: BouncyRecyclerView
) : LinearLayoutManager(context, HORIZONTAL, false) {

    override fun getPaddingLeft(): Int = getPadding()

    override fun getPaddingRight(): Int = getPadding()

    private fun getPadding() = bouncyRecyclerView.children.firstOrNull()?.let {
        if (it.measuredWidth > 0) {
            return bouncyRecyclerView.width / 2 - it.measuredWidth / 2
        } else 0
    } ?: 0

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)

        // Only resize when the state is settled. Do not resize during the RecyclerView "setup"
        if (state != null && !state.isPreLayout && !state.isMeasuring) {
            ZoomPageTransformer.adjustViewSizes(bouncyRecyclerView)
        }
    }

}
