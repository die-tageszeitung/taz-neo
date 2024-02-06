package de.taz.app.android.ui.home.page.coverflow

import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory
import androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_RIGHT

private const val OVERSCROLL_ANIMATION_SIZE = 0.5f
private const val FLING_ANIMATION_SIZE = 0.5f

/**
 * Create a BouncyEdgeEffect that allows to overscroll the RecycleView items and bounces them
 * back into position when the pointer is released.
 * This is based on the behavior of https://github.com/valkriaine/Bouncy which is licensed under the
 * Apache License, Version 2.0, January 2004, http://www.apache.org/licenses/
 */
class BouncyEdgeEffect(private val recyclerView: RecyclerView, private val direction: Int) :
    EdgeEffect(recyclerView.context) {

    companion object {
        val Factory = object : EdgeEffectFactory() {
            override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                return BouncyEdgeEffect(recyclerView, direction)
            }
        }
    }

    private val springAnimation = SpringAnimation(recyclerView, SpringAnimation.TRANSLATION_X)
        .setSpring(
            SpringForce()
                .setFinalPosition(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
        )

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        onPullAnimation(deltaDistance)
        return 0f
    }

    override fun onPull(deltaDistance: Float) {
        onPullAnimation(deltaDistance)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        onPullAnimation(deltaDistance)
    }

    private fun onPullAnimation(deltaDistance: Float) {
        val sign = if (direction == DIRECTION_RIGHT) -1 else 1
        val delta = sign * recyclerView.width * deltaDistance * OVERSCROLL_ANIMATION_SIZE
        recyclerView.translationX += delta
        springAnimation.cancel()
    }

    override fun onRelease() {
        if (recyclerView.translationX != 0f) {
            springAnimation.start()
        }
    }

    override fun onAbsorb(velocity: Int) {
        val sign = if (direction == DIRECTION_RIGHT) -1 else 1
        val startVelocity = sign * velocity * FLING_ANIMATION_SIZE
        springAnimation.setStartVelocity(startVelocity).start()
    }

    override fun finish() {
        recyclerView.translationX = 0f
        springAnimation.cancel()
    }

    override fun isFinished(): Boolean {
        return !springAnimation.isRunning
    }

    override fun draw(canvas: Canvas?): Boolean {
        return false
    }

    override fun getDistance(): Float {
        return 0f
    }
}