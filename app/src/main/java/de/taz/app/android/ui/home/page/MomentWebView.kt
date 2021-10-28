package de.taz.app.android.ui.home.page

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/**
 * WebView to be used in [de.taz.app.android.ui.cover.CoverView]
 * overwriting onTouchEvent ensures, that we do not handle any click events
 * and propagate them to the parent
 */
class MomentWebView @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyle: Int = 0
) : WebView(context, attributeSet, defStyle) {

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun performClick(): Boolean {
        return false
    }
}