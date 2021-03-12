package de.taz.app.android.ui.home.page.coverflow

import android.view.View
import de.taz.app.android.R
import de.taz.app.android.util.Log
import kotlin.math.abs
import kotlin.math.max

object ZoomPageTransformer {
    private const val MIN_SCALE = 0.85f
    private const val SCALE_DIFF = 1 - MIN_SCALE
    private const val GAP_MODIFIER = 10f

    private fun translationXAtScale(view: View, position: Float, scaleFactor: Float): Float =
        view.run {
            val child = view.findViewById<View>(R.id.moment_container)
            val border = (width - child.width).toFloat()
            (border * position)
        }

    val log by Log

    fun transformPage(view: View, position: Float) = view.run {
        scaleX = MIN_SCALE
        scaleY = MIN_SCALE

        translationX = translationXAtScale(view, position, MIN_SCALE)

        when {
            position <= 1 && position >= -1 -> {
                val scaleFactor = max(MIN_SCALE, 1 - (SCALE_DIFF * abs(position)))
                translationX = translationXAtScale(view, position, scaleFactor)
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
        }
    }
}