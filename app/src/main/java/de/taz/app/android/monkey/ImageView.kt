package de.taz.app.android.monkey

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.annotation.DrawableRes

fun ImageView.animatedChange(newImage: Bitmap, targetAlpha: Float= 1f) {
    animate().alpha(0f).setDuration(100L).withEndAction {
        setImageBitmap(newImage)
        imageAlpha = (targetAlpha * 255).toInt()
        animate().alpha(1f).duration = 100
    }
}

