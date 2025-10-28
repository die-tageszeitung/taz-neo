package de.taz.app.android.coachMarks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.tracking.Tracker
import kotlin.math.abs


abstract class BaseCoachMark(@LayoutRes private val layoutResId: Int) : Fragment(layoutResId) {

    protected var menuItem: View? = null
    protected var textString: String? = null
    protected var resizeIcon: Boolean = false
    protected var useShortArrow: Boolean = false

    // Some menuItems are at the position where the close icon ist
    // In that case the coachmark should be the last and we move the close button
    // down to where the next icon is
    var moveCloseButtonToWhereNextIs = false

    protected lateinit var generalDataStore: GeneralDataStore
    protected lateinit var authHelper: AuthHelper
    protected lateinit var tracker: Tracker
    protected var isPortrait = true
    protected var isTabletMode = false
    private var showConditions = isPortrait || isTabletMode

    override fun onAttach(context: Context) {
        super.onAttach(context)
        isPortrait =
            context.resources.displayMetrics.heightPixels > context.resources.displayMetrics.widthPixels
        isTabletMode = context.resources.getBoolean(R.bool.isTablet)
        showConditions = isPortrait || isTabletMode
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (resizeIcon) {
            resizeCoachMarkIcon()
        }
        getLocationAndSetPosition()
        onCoachMarkCreated()
    }

    override fun onResume() {
        super.onResume()
        tracker.trackCoachMarkShow(requireContext().resources.getResourceName(layoutResId))
    }

    override fun onStop() {
        super.onStop()
        tracker.trackCoachMarkClose(requireContext().resources.getResourceName(layoutResId))
    }

    protected open fun onCoachMarkCreated() {}

    private fun getLocationAndSetPosition() {
        val location = this.getLocation()
        this.setPositionAndText(location)
    }

    private fun getLocation(): IntArray {
        val location = intArrayOf(0, 0)
        menuItem?.getLocationOnScreen(location)
        return location
    }

    private fun setPositionAndText(location: IntArray) {
        val coachMarkIconWrapper =
            requireView().findViewById<View>(R.id.coach_mark_icon_wrapper)
        val canvas = requireView().findViewById<ImageView>(R.id.coach_mark_canvas)

        // Before edge-to-edge was enforced (35) we need to subtract the status bar height from y
        val statusBarHeight = if (Build.VERSION.SDK_INT < 35) {
            getStatusBarHeight()
        } else {
            0
        }

        coachMarkIconWrapper?.apply {
            x = location[0].toFloat()
            y = location[1].toFloat() - statusBarHeight
            textString?.let {
                findViewById<TextView>(R.id.coach_mark_text_view)?.text = it
            }
        }

        if (canvas != null && coachMarkIconWrapper != null && menuItem != null) {
            drawLineToIconFromMid(coachMarkIconWrapper, canvas)
        }
    }

    private fun resizeCoachMarkIcon() {
        val coachMarkIconWrapper =
            requireView().findViewById<View>(R.id.coach_mark_icon_wrapper)

        menuItem?.let {
            coachMarkIconWrapper.apply {
                layoutParams.height = it.height
                layoutParams.width = it.width
            }
        }
    }

    private fun drawLineToIconFromMid(icon: View, imageView: ImageView) {
        // factor of how much % should be the padding
        val factorPaddingToCenter = if (useShortArrow) {
            0.65
        } else {
            0.25
        }

        // unfortunately icon.width is not working, so we take the menuItem!!.width
        val width = menuItem!!.width
        val height = menuItem!!.height

        val bitmap = createBitmap(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels
        )

        // Create a canvas with transparent background
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        imageView.setImageBitmap(bitmap)

        val paint = Paint()
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1 * resources.displayMetrics.density
        paint.isAntiAlias = true

        // Get the middle of icon
        val midOfIconX = icon.x + width * 0.5
        val midOfIconY = icon.y + height * 0.5

        // get the center
        val centerX = resources.displayMetrics.widthPixels * 0.5
        val centerY = resources.displayMetrics.heightPixels * 0.5

        val slope = abs(centerY - midOfIconY) / abs(centerX - midOfIconX)

        val startCoordinates =
            if (-height * 0.5 <= slope * width * 0.5 && slope * width * 0.5 <= height * 0.5) {
                if (centerX > midOfIconX) {
                    // intersects the right edge
                    val x = midOfIconX + width * 0.5
                    val y = if (midOfIconY < centerY) {
                        midOfIconY + (slope * width * 0.5)
                    } else {
                        midOfIconY - (slope * width * 0.5)
                    }
                    x to y
                } else {
                    // intersects the left edge
                    val x = midOfIconX - width * 0.5
                    val y = if (midOfIconY < centerY) {
                        midOfIconY + (slope * width * 0.5)
                    } else {
                        midOfIconY - (slope * width * 0.5)
                    }
                    x to y
                }
            } else {
                if (centerY > midOfIconY) {
                    // intersects the bottom edge
                    val x = if (midOfIconX < centerX) {
                        midOfIconX + (height * 0.5) / slope
                    } else {
                        midOfIconX - (height * 0.5) / slope
                    }
                    val y = midOfIconY + height * 0.5
                    x to y
                } else {
                    // intersects the top edge
                    val x =
                        if (midOfIconX < centerX) {
                            midOfIconX + (height * 0.5) / slope
                        } else {
                            midOfIconX - (height * 0.5) / slope
                        }
                    val y = midOfIconY - height * 0.5
                    x to y
                }
            }

        val stopX = (centerX - (centerX - midOfIconX) * factorPaddingToCenter).toFloat()
        val stopY = (centerY - (centerY - midOfIconY) * factorPaddingToCenter).toFloat()

        // Draw the line
        canvas.drawLine(
            startCoordinates.first.toFloat(),
            startCoordinates.second.toFloat(),
            stopX,
            stopY,
            paint
        )

        // Set this bitmap to the ImageView
        imageView.setImageBitmap(bitmap)
    }

    private fun getStatusBarHeight(): Int {
        val rectangle = Rect()
        requireActivity().window.decorView.getWindowVisibleDisplayFrame(rectangle)
        return rectangle.top
    }
}