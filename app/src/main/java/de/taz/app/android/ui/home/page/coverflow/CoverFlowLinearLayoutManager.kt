package de.taz.app.android.ui.home.page.coverflow

import android.content.Context
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import com.factor.bouncy.BouncyRecyclerView

class CoverFlowLinearLayoutManager(
    context: Context,
    private val bouncyRecyclerView: BouncyRecyclerView
) : LinearLayoutManager(context, HORIZONTAL, false) {
    override fun getPaddingLeft(): Int = getPadding()

    override fun getPaddingRight(): Int = getPadding()

    /**
     * We need to create a padding allow this recyclerview to snap also the first and the last item
     * in the middle of the screen. Without it the first item would stick to the left side of the screen
     */
    private fun getPadding() = bouncyRecyclerView.children.firstOrNull()?.let {
        if (it.measuredWidth > 0) {
            return bouncyRecyclerView.width / 2 - it.measuredWidth / 2
        } else 0
    } ?: 0
   
}
