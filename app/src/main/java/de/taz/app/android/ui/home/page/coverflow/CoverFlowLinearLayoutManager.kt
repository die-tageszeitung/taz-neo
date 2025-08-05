package de.taz.app.android.ui.home.page.coverflow

import android.content.Context
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper

class CoverFlowLinearLayoutManager(
    context: Context,
    private val recyclerView: RecyclerView,
    private val snapHelper: GravitySnapHelper,
) : LinearLayoutManager(context, HORIZONTAL, false) {


    override fun getPaddingLeft(): Int = getPadding()

    override fun getPaddingRight(): Int = getPadding()

    @Suppress("KotlinConstantConditions")
    private fun getPadding() = recyclerView.children.firstOrNull()?.let {
        if (it.measuredWidth > 0) {
            return recyclerView.width / 2 - it.measuredWidth / 2
        } else 0
    } ?: 0

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        ZoomPageTransformer.adjustViewSizes(recyclerView)
        snapHelper.updateSnap(true, true)
    }

}
