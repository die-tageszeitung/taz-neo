package de.taz.app.android.ui.home.page.coverflow

import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.util.Log
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

object ZoomPageTransformer {

    private fun translationXAtScale(view: View, position: Float): Float =
        view.run {
            val child = view.findViewById<View>(R.id.moment_container)
            val border = (width - child.width).toFloat()
            val result = border * position
            // this factor is found by experimentation :)
            // The idea behind is: the more outer you are (bigger position) the higher the factor should be
            val factor = min((1 + position.pow(2)).pow(8), 5.5f)
            val isLandscape =
                resources.displayMetrics.heightPixels < resources.displayMetrics.widthPixels
            if (isLandscape) result * factor else result
        }


    private fun transformPage(view: View, position: Float) = view.run {
        val minScale = resources.getFraction(R.fraction.cover_scale_factor, 1, 1)
        val scaleDiff = 1 - minScale

        scaleX = minScale
        scaleY = minScale
        translationX = translationXAtScale(view, position)

        if (position in -1.0f..1.0f) {
            val scaleFactor = max(minScale, 1 - (scaleDiff * abs(position)))
            scaleX = scaleFactor
            scaleY = scaleFactor
        }
    }

    fun adjustViewSizes(recyclerView: RecyclerView) {
        recyclerView.apply {
            children.forEach { child ->
                val childPosition = (child.left + child.right) / 2f
                val center = width / 2
                transformPage(child, (center - childPosition) / width)
            }
        }
    }
}