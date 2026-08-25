package de.taz.app.android.monkey

import android.R
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideViewOnScrollBehavior

fun View.setHideViewOnScrollBehavior(behavior: HideViewOnScrollBehavior<View>?) {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
    if (coordinatorLayoutParams != null) {
        coordinatorLayoutParams.behavior = behavior
        layoutParams = coordinatorLayoutParams
    }
}

fun View.getHideViewOnScrollBehavior(): HideViewOnScrollBehavior<View>? {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
    return coordinatorLayoutParams?.behavior as? HideViewOnScrollBehavior
}

fun View.getLogoScrollBehavior(): LogoScrollBehavior? =
    getHideViewOnScrollBehavior() as? LogoScrollBehavior

private const val DEFAULT_ENTER_ANIMATION_DURATION_MS = 1225
private const val DEFAULT_EXIT_ANIMATION_DURATION_MS = 1175


class LogoScrollBehavior(
    onScrolledIn: () -> Unit,
    onScrolledOut: () -> Unit,
) : HideViewOnScrollBehavior<View>() {
    private var listenerDisabled = false
    private var pendingHideAnimate: Boolean? = null
    private var pendingShowAnimate: Boolean? = null

    private var enterAnimDuration: Int = DEFAULT_ENTER_ANIMATION_DURATION_MS
    public val enterAnimationDuration
        get() = this.enterAnimDuration

    private var exitAnimDuration: Int = DEFAULT_EXIT_ANIMATION_DURATION_MS
    public val exitAnimationDuration
        get() = this.exitAnimDuration

    init {
        updateAnimDurations()
        setViewEdge(EDGE_LEFT)

        addOnScrollStateChangedListener { _, scrollState ->
            if (listenerDisabled) {
                return@addOnScrollStateChangedListener
            }

            if (scrollState == STATE_SCROLLED_IN) {
                onScrolledIn()
            } else {
                onScrolledOut()
            }
        }

    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        val result = super.onLayoutChild(parent, child, layoutDirection)
        updateAnimDurations()

        pendingHideAnimate?.let { animate ->
            val size = getBehaviorSize()
            if (size > 0) {
                pendingHideAnimate = null
                try {
                    val currentStateField =
                        HideViewOnScrollBehavior::class.java.getDeclaredField("currentState")
                    currentStateField.isAccessible = true
                    if (animate) {
                        currentStateField.set(this, 2) // STATE_SCROLLED_IN
                        super.slideOut(child, true)
                    } else {
                        currentStateField.set(this, 1) // STATE_SCROLLED_OUT
                        child.translationX = -size.toFloat()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        pendingShowAnimate?.let { animate ->
            val size = getBehaviorSize()
            if (size > 0) {
                pendingShowAnimate = null
                try {
                    val currentStateField =
                        HideViewOnScrollBehavior::class.java.getDeclaredField("currentState")
                    currentStateField.isAccessible = true
                    if (animate) {
                        currentStateField.set(this, 1) // STATE_SCROLLED_OUT
                        super.slideIn(child, true)
                    } else {
                        currentStateField.set(this, 2) // STATE_SCROLLED_IN
                        child.translationX = 0f
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        return result
    }

    override fun slideOut(view: View, animate: Boolean) {
        super.slideOut(view, animate)
        if (getBehaviorSize() <= 0) {
            pendingHideAnimate = animate
        }
    }

    override fun slideIn(view: View, animate: Boolean) {
        super.slideIn(view, animate)
        if (getBehaviorSize() <= 0) {
            pendingShowAnimate = animate
        }
        pendingHideAnimate = null
    }

    private fun getBehaviorSize(): Int {
        return try {
            val sizeField = HideViewOnScrollBehavior::class.java.getDeclaredField("size")
            sizeField.isAccessible = true
            sizeField.get(this) as Int
        } catch (e: Exception) {
            0
        }
    }

    public fun setEnterAnimationDuration(duration: Int = DEFAULT_ENTER_ANIMATION_DURATION_MS) {
        this.enterAnimDuration = duration
        updateAnimDurations()
    }

    public fun setExitAnimationDuration(duration: Int = DEFAULT_EXIT_ANIMATION_DURATION_MS) {
        this.exitAnimDuration = duration
        updateAnimDurations()
    }

    private fun updateAnimDurations() {
        val enterAnimationDurationField =
            HideViewOnScrollBehavior::class.java.getDeclaredField("enterAnimDuration")
        enterAnimationDurationField.isAccessible = true
        enterAnimationDurationField.set(this, enterAnimDuration)

        val exitAnimationDurationField =
            HideViewOnScrollBehavior::class.java.getDeclaredField("exitAnimDuration")
        exitAnimationDurationField.isAccessible = true
        exitAnimationDurationField.set(this, exitAnimDuration)
    }

    fun slideInConditionally(view: View, animate: Boolean = true, disableListener: Boolean = true) {
        if (!isScrolledIn) {
            if (disableListener) {
                listenerDisabled = true
            }
            slideIn(view, animate)
            if (disableListener) {
                listenerDisabled = false
            }
        }
    }


    fun slideOutConditionally(
        view: View,
        animate: Boolean = true,
        disableListener: Boolean = true,
    ) {
        if (!isScrolledOut) {
            if (disableListener) {
                listenerDisabled = true
            }
            slideOut(view, animate)
            if (disableListener) {
                listenerDisabled = false
            }
        }
    }

}

/**
 * Sets up the scroll behavior for the logo that toggles between burger and feed logo
 * based on scroll position.
 */
fun View.setupLogoScrollBehavior(
    enabled: Boolean,
    logoScrollBehavior: LogoScrollBehavior,
) {
    if (enabled) {
        setHideViewOnScrollBehavior(logoScrollBehavior)
    } else {
        setHideViewOnScrollBehavior(null)
    }
}