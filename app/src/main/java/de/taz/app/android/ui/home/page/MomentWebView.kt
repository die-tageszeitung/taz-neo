package de.taz.app.android.ui.home.page

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

class MomentWebView @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyle: Int = 0
) : WebView(context, attributeSet, defStyle) {

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // do not handle any touch events
        return false
    }
}