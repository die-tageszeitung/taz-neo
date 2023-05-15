package de.taz.app.android.audioPlayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import de.taz.app.android.R

/**
 * Wrapper around [ConstraintLayout] with additional helpers to control the visibility of the
 * expanded players audio image in small height scenarios.
 */
class ExpandedPlayerConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // If the expanded player results in being too high, we'll try to
        // hide the expanded Audio Image and re-measure everything
        val tooSmall = (measuredHeightAndState and View.MEASURED_STATE_TOO_SMALL) != 0
        val expandedAudioImage = findViewById<View>(R.id.expanded_audio_image)
        if (tooSmall && expandedAudioImage?.isVisible == true) {
            expandedAudioImage.isVisible = false
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}