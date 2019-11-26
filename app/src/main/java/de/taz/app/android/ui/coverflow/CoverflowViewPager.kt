package de.taz.app.android.ui.coverflow

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.ViewPager
import de.taz.app.android.util.Log


class CoverflowViewPager @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : ViewPager(context, attributeSet) {
    val log by Log

    init {
        setPageTransformer(true, ZoomPageTransformer())
        offscreenPageLimit = 3
    }
}