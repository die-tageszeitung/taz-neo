package de.taz.app.android.ui.home.page.coverflow

import android.view.View
import de.taz.app.android.R
import de.taz.app.android.util.Log
import kotlin.math.abs
import kotlin.math.max

object ZoomPageTransformer {
    private fun translationXAtScale(view: View, position: Float): Float =
        view.run {
            val child = view.findViewById<View>(R.id.moment_container)
            val border = (width - child.width).toFloat()
            (border * position)
        }

    val log by Log

    fun transformPage(view: View, position: Float) = view.run {
        val minScale = resources.getFraction(R.fraction.cover_scale_factor, 1, 1)
        val scaleDiff = 1 - minScale

        log.debug("******** Fraction from ressources: $minScale")
        scaleX = minScale
        scaleY = minScale

        translationX = translationXAtScale(view, position)

        when {
            position <= 1 && position >= -1 -> {
                val scaleFactor = max(minScale, 1 - (scaleDiff * abs(position)))
                translationX = translationXAtScale(view, position)
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
        }
    }
}