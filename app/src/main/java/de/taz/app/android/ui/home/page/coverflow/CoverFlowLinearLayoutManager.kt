package de.taz.app.android.ui.home.page.coverflow

import android.content.Context
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CoverFlowLinearLayoutManager(
    context: Context,
    private val recyclerView: RecyclerView,
    private val estimatedWidth: Int,
) : LinearLayoutManager(context, HORIZONTAL, false) {


    override fun getPaddingLeft(): Int = getPadding()

    override fun getPaddingRight(): Int = getPadding()

    private fun getPadding(): Int {
        val width = recyclerView.children.firstOrNull()?.measuredWidth ?: estimatedWidth
        return if (width > 0) {
            recyclerView.width / 2 - width / 2
        } else 0
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        ZoomPageTransformer.adjustViewSizes(recyclerView)
    }

}
