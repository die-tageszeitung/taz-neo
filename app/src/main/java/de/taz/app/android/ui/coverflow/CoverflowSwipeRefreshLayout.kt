package de.taz.app.android.ui.coverflow

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import de.taz.app.android.util.Log

class CoverflowSwipeRefreshLayout @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : SwipeRefreshLayout(context, attributeSet) {
    val log by Log

}