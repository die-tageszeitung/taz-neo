package de.taz.app.android.monkey

import android.R
import android.graphics.Bitmap
import android.graphics.drawable.TransitionDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import android.view.animation.Transformation
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable

fun ImageView.transitionTo(newImage: Bitmap) {
    val transitionDrawable =
        TransitionDrawable(arrayOf(drawable, newImage.toDrawable(context.resources)))
    setImageDrawable(transitionDrawable)
    transitionDrawable.startTransition(100)
}

fun ImageView.animatedChange(newImage: Bitmap) {

    val animOut: Animation = AnimationUtils.loadAnimation(context, R.anim.fade_in)
    val animIn: Animation = AnimationUtils.loadAnimation(context, R.anim.fade_in)

    animOut.duration = 0L
    animIn.duration = 500L

    animOut.setAnimationListener(object : AnimationListener {
        override fun onAnimationStart(animation: Animation) {}
        override fun onAnimationRepeat(animation: Animation) {}
        override fun onAnimationEnd(animation: Animation) {
            setImageBitmap(newImage)
            animIn.setAnimationListener(object : AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {}
            })
            startAnimation(animIn)
        }
    })
    startAnimation(animOut)
}

