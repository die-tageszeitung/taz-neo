package de.taz.app.android.ui.drawer

import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.GravityCompat
import de.taz.app.android.HIDE_LOGO_DELAY_MS
import de.taz.app.android.LOGO_ANIMATION_DURATION_MS
import de.taz.app.android.R

private const val UNKNOWN_DRAWER_LOGO_WIDTH = -1

/**
 * This controller handles the UI updates of the DrawerState collected from the [drawerAndLogoViewModel]
 * in [TazViewerFragment] and in [PdfPagerActivity].
 * Additionally it handles the offset in onDrawerSlide of their drawer.
 *
 */
class DrawerViewController(
    context: Context,
    private val drawerLayout: DrawerLayout,
    private val drawerLogoWrapper: View,
    private val navView: NavigationView,
    private val setLogoHiddenState: (Boolean) -> Unit
) {

    private val resources = context.resources

    var drawerLogoWidth: Int = UNKNOWN_DRAWER_LOGO_WIDTH
        set(value) {
            field = value
            // Update the current logo state.
            // As this is only called once when the drawer first initializes we animate the change
            currentState?.let {
                animateDrawerLogoTranslation(it.hideLogo)
            }
        }

    private var isLogoHidden = false
    private var currentState: DrawerState? = null

    fun handleDrawerState(state: DrawerState) {
        // We store the previous percentage to be able to detect full jumps that we may want to animate
        val prevPercentHide = currentState?.percentHide ?: 0f
        currentState = state

        when (state) {
            is DrawerState.Closed -> {
                when {
                    // If the main logo state changed, we will show/hide the logo with an animation
                    isLogoHidden != state.hideLogo -> {
                        isLogoHidden = state.hideLogo
                        animateDrawerLogoTranslation(state.hideLogo)
                    }

                    // If the percentage jumps 100%, we will simply trigger a main logo change, which will result in an animation
                    !isLogoHidden && prevPercentHide == 0f && state.percentHide == 1f -> setLogoHiddenState(true)
                    isLogoHidden && prevPercentHide == 1f && state.percentHide == 0f -> setLogoHiddenState(false)

                    // If there is some in-between change we move the logo according to to percentages
                    state.percentHide > 0f && state.percentHide < 1f -> hideLogoByPercent(state.percentHide)

                    // If logo ends up at an extreme, we set force the main logo state to fit it
                    state.percentHide == 0f || state.percentHide == 1f -> {
                        isLogoHidden = (state.percentHide == 1f)
                        hideLogoByPercent(state.percentHide)
                        setLogoHiddenState(isLogoHidden)
                    }
                }
                closeDrawer()
            }

            is DrawerState.Open -> {
                isLogoHidden = state.hideLogo
                openDrawer()
            }
        }
    }

    /**
     * Calculate the offsets of the drawerLogo for onDrawerSlide function.
     * The [slideOffset] can be between 0 (closed drawer) and 1 (open drawer).
     */
    fun handleOnDrawerSlider(slideOffset: Float) {
        // Ignore any events before we the logo is even set
        if (drawerLogoWidth == UNKNOWN_DRAWER_LOGO_WIDTH)
            return

        val translationX = calculateTranslationXOnDrawerSlide(slideOffset)
        drawerLogoWrapper.translationX = translationX
    }

    /**
     * Calculate the offsets of the drawerLogo for onDrawerSlide function.
     * The [slideOffset] can be between 0 (closed drawer) and 1 (open drawer).
     *
     * The offset for the closed drawer is the default translationX of the logo.
     * It is determined by the dimension [R.dimen.drawer_logo_translation_x] or by the logo width
     * minus the [R.dimen.drawer_logo_peak_when_hidden]
     *
     * The offset for the open drawer is determined by the gap between drawer and screen border
     * minus the drawerLogo.width
     */
    private fun calculateTranslationXOnDrawerSlide(slideOffset: Float): Float {
        val screenWidth = resources.displayMetrics.widthPixels
        val logoTranslationForClosedDrawer = if (isLogoHidden) {
            -drawerLogoWidth +
                    resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden)
        } else {
            resources.getDimensionPixelSize(R.dimen.drawer_logo_translation_x)
        }
        val drawerWidthLogoBiggerThenScreenWidth =
            drawerLogoWidth + navView.width > screenWidth

        val logoTranslationForOpenDrawer = if (drawerWidthLogoBiggerThenScreenWidth) {
            screenWidth - navView.width - drawerLogoWidth
        } else {
            resources.getDimensionPixelSize(R.dimen.drawer_logo_translation_x)
        }
        // translation needed for logo when drawer is open (slideOffset = 1) with logo too wide:
        val offsetOnOpenDrawer = slideOffset * logoTranslationForOpenDrawer

        // translation needed when drawer is closed (slideOffset = 0):
        val offsetOnClosedDrawer = (1 - slideOffset) * logoTranslationForClosedDrawer
        return offsetOnOpenDrawer + offsetOnClosedDrawer
    }

    private fun animateDrawerLogoTranslation(isHidden: Boolean) {
        if (isHidden) {
            hideDrawerLogoAnimatedWithDelay()
        } else {
            showDrawerLogoAnimated()
        }
    }

    private fun hideDrawerLogoAnimatedWithDelay() {
        // Ignore any events before we the logo is even set
        if (drawerLogoWidth == UNKNOWN_DRAWER_LOGO_WIDTH)
            return

        val transX =
            -drawerLogoWidth.toFloat() + resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden)
        if (transX != drawerLogoWrapper.translationX) {
            drawerLogoWrapper.animate()
                .withEndAction {
                    // add additional area where clicks are handled to open the drawer
                    val widthWhereToHandleLogoClick =
                        resources.getDimensionPixelSize(R.dimen.drawer_logo_peak__when_hidden_click_area)
                    drawerLayout.updateDrawerLogoBoundingBox(
                        width = widthWhereToHandleLogoClick,
                        height = drawerLogoWrapper.height
                    )
                }
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(HIDE_LOGO_DELAY_MS)
                .translationX(transX)
                .setInterpolator(AccelerateDecelerateInterpolator())
        }
    }

    private fun showDrawerLogoAnimated() {
        // Ignore any events before we the logo is even set
        if (drawerLogoWidth == UNKNOWN_DRAWER_LOGO_WIDTH)
            return

        val transX = resources.getDimension(R.dimen.drawer_logo_translation_x)
        if (drawerLogoWrapper.translationX != transX) {
            drawerLogoWrapper.animate()
                .withEndAction {
                    drawerLayout.updateDrawerLogoBoundingBox(
                        drawerLogoWidth,
                        drawerLogoWrapper.height
                    )
                }
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(0L)
                .translationX(transX)
                .setInterpolator(AccelerateDecelerateInterpolator())
        }
    }

    /**
     * Hide the logo relatively to the relative amount given.
     * Attention: We have some default translation when the logo there and when it is hidden
     * @param [percentToHide] Float between [0,1] - indicating how much to hide
     */
    private fun hideLogoByPercent(percentToHide: Float) {
        // Ignore any events before we the logo is even set
        if (drawerLogoWidth == UNKNOWN_DRAWER_LOGO_WIDTH)
            return

        val hiddenTrans = resources.getDimension(R.dimen.drawer_logo_translation_x)
        val openTrans = resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden)

        val transInHiddenState = (1 - percentToHide) * hiddenTrans
        val transInOpenState = percentToHide * openTrans

        val transX =
            percentToHide * drawerLogoWidth - (transInHiddenState + transInOpenState)
        drawerLogoWrapper.translationX = -transX

        val visiblePart = drawerLogoWidth - transX

        val widthWhereToHandleLogoClick =
            visiblePart + percentToHide *
                    resources.getDimensionPixelSize(R.dimen.drawer_logo_peak__when_hidden_click_area)

        drawerLayout.updateDrawerLogoBoundingBox(
            width = widthWhereToHandleLogoClick.toInt(),
            height = drawerLogoWrapper.height
        )
    }

    private fun closeDrawer() {
        if (isDrawerOpen()) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun openDrawer() {
        if (!isDrawerOpen()) {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun isDrawerOpen(): Boolean {
        return drawerLayout.isDrawerOpen(GravityCompat.START)
    }
}