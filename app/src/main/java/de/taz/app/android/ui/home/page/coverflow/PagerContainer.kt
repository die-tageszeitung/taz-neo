package de.taz.app.android.ui.home.page.coverflow

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.util.Log

/**
 * PagerContainer: A layout that displays a ViewPager with its children that are outside
 * the typical pager bounds.
 */
class PagerContainer : ConstraintLayout {

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context) : super(context)

    private lateinit var viewPager: ViewPager2
    private val center = Point()
    private val initialTouch = Point()
    private var needsRedraw = false

    val log by Log

    init {
        //Disable clipping of children so non-selected pages are visible
        clipChildren = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        try {
            viewPager = getChildAt(0) as ViewPager2
            viewPager.registerOnPageChangeCallback(PagerContainerChangeCallback())
        } catch (e: TypeCastException) {
            throw IllegalStateException("The root child of PagerContainer must be a ViewPager")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        center.x = w / 2
        center.y = h / 2
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouch.set(it.x.toInt(), it.y.toInt())
                }
                else -> {
                    it.offsetLocation(
                        (center.x - initialTouch.x).toFloat(),
                        (center.y - initialTouch.y).toFloat()
                    )
                }
            }
        }
        return viewPager.dispatchTouchEvent(event)
    }


    inner class PagerContainerChangeCallback : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {}

        override fun onPageScrollStateChanged(state: Int) {
            needsRedraw = state != ViewPager2.SCROLL_STATE_IDLE
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            if (needsRedraw) invalidate()
        }
    }
}
