package de.taz.app.android.ui.drawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import de.taz.app.android.HIDE_LOGO_DELAY_MS
import de.taz.app.android.LOGO_ANIMATION_DURATION_MS
import de.taz.app.android.R
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNKNOWN_DRAWER_LOGO_WIDTH = -1
private const val NO_TRANSLATION = 0f

/**
 * This controller handles the UI updates of the DrawerState collected from the [DrawerAndLogoViewModel]
 * in [TazViewerFragment] and in [PdfPagerFragment].
 * Additionally it handles the offset in onDrawerSlide of their drawer.
 */
class DrawerViewController(
    context: Context,
    private val drawerLayout: DrawerLayout,
    private val drawerLogoWrapper: View,
    private val navView: View,
) {

    private val resources = context.resources
    private val imageRepository = ImageRepository.getInstance(context)
    private val storageService = StorageService.getInstance(context)
    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(context)
    private val glide = Glide.with(context)

    var drawerLogoWidth: Int = UNKNOWN_DRAWER_LOGO_WIDTH

    private var isLogoBurger = false
    private var isLogoClose = false
    private var feedLogoDrawable: Drawable? = null
    private var wasHidden = false

    fun handleDrawerLogoState(state: DrawerState) {
        when (state) {
            is DrawerState.Closed -> {
                when {
                    state.isHidden -> {
                        if (isLogoBurger && !state.isBurger) {
                            CoroutineScope(Dispatchers.Main).launch {
                                setFeedLogo()
                                hideDrawerLogoAnimatedWithDelay()
                            }
                        } else {
                            hideDrawerLogoAnimatedWithDelay()
                        }
                        wasHidden = true
                    }

                    // If there is some in-between change
                    // we need to set to feed logo if we have burger icon
                    state.percentMorphedToBurger > 0f && state.percentMorphedToBurger < 1f -> {
                        if (isLogoBurger) {
                            CoroutineScope(Dispatchers.Main).launch {
                                setFeedLogo()
                            }
                        }
                    }

                    // If logo ends up at an extreme, we set force the main logo state to fit it
                    state.percentMorphedToBurger == 1f -> {
                        if (!isLogoBurger || state.isBurger || isLogoClose) {
                            setBurgerIcon()
                        }
                    }

                    state.percentMorphedToBurger == 0f -> {
                        if (wasHidden) {
                            showDrawerLogoAnimated()
                        }
                        if (isLogoBurger || isLogoClose) {
                            CoroutineScope(Dispatchers.Main).launch {
                                setFeedLogo()
                            }
                        }
                        wasHidden = false
                    }
                }
                if (!state.isHidden) {
                    morphLogosByPercent(state.percentMorphedToBurger)
                }
                closeDrawer()
            }

            is DrawerState.Open -> {
                openDrawer()
            }
        }
    }

    /**
     * Calculate the offsets of the drawerLogo for onDrawerSlide function.
     * The [slideOffset] can be between 0 (closed drawer) and 1 (open drawer).
     */
    fun handleOnDrawerSlider(slideOffset: Float) {
        // Decide on icon:
        if (slideOffset > 0 && !isDrawerOpen()) {
            if (!isLogoClose) {
                setCloseIcon()
            }
        } else {
            if (isLogoBurger) {
                setBurgerIcon()
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    setFeedLogo()
                }
            }
        }
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
     * The offset for the closed drawer is the default translationX of the feed logo or
     * [NO_TRANSLATION] for the burger logo.
     *
     * The offset for the open drawer is determined by the gap between drawer and screen border
     * minus the drawerLogo.width
     */
    private fun calculateTranslationXOnDrawerSlide(slideOffset: Float): Float {
        val screenWidth = resources.displayMetrics.widthPixels

        val logoTranslationForClosedDrawer = if (isLogoBurger) {
            NO_TRANSLATION.toInt()
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

        val transX = if (isLogoBurger) {
            NO_TRANSLATION
        } else {
            resources.getDimension(R.dimen.drawer_logo_translation_x)
        }
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
     * Morph the logo by the given [percent] to the burger icon.
     * @param [percent] Float between [0,1] - indicating how much to morph to burger icon
     */
    private fun morphLogosByPercent(percent: Float) {
        // Ignore any events before we the logo is even set
        if (drawerLogoWidth == UNKNOWN_DRAWER_LOGO_WIDTH)
            return

        val hiddenTrans = resources.getDimension(R.dimen.drawer_logo_translation_x)
        val openTrans = resources.getDimensionPixelSize(R.dimen.drawer_burger_menu_width)

        val transInHiddenState = (1 - percent) * hiddenTrans
        val transInOpenState = percent * openTrans

        val transX =
            percent * drawerLogoWidth - (transInHiddenState + transInOpenState)

        drawerLogoWrapper.translationX = -transX

        val visiblePart = drawerLogoWidth - transX

        val widthWhereToHandleLogoClick =
            visiblePart + percent *
                    resources.getDimensionPixelSize(R.dimen.drawer_logo_peak__when_hidden_click_area)

        drawerLayout.updateDrawerLogoBoundingBox(
            width = widthWhereToHandleLogoClick.toInt(),
            height = drawerLogoWrapper.height
        )
    }

    private fun setBurgerIcon() {
        isLogoBurger = true
        isLogoClose = false

        val widthFromDimens = resources.getDimensionPixelSize(R.dimen.drawer_burger_menu_width)
        drawerLogoWidth = widthFromDimens
        drawerLogoWrapper.updateLayoutParams {
            width = widthFromDimens
        }
        val burgerDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_burger_menu, null)
        drawerLogoWrapper.translationX = NO_TRANSLATION
        drawerLogoWrapper.findViewById<ImageView>(R.id.drawer_logo).apply {
            updateLayoutParams {
                width = widthFromDimens
            }
            setImageDrawable(burgerDrawable)
        }
    }

    private fun setCloseIcon() {
        Log.e("!!!", "set Close")
        isLogoClose = true
        val widthFromDimens = resources.getDimensionPixelSize(R.dimen.drawer_burger_menu_width)
        drawerLogoWidth = widthFromDimens
        drawerLogoWrapper.updateLayoutParams {
            width = widthFromDimens
        }
        val closeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_close_drawer, null)
        drawerLogoWrapper.translationX = NO_TRANSLATION
        drawerLogoWrapper.findViewById<ImageView>(R.id.drawer_logo).apply {
            updateLayoutParams {
                width = widthFromDimens
            }
            setImageDrawable(closeDrawable)
        }
    }

    suspend fun setFeedLogo(): Drawable? {
        isLogoBurger = false
        isLogoClose = false

        val defaultDrawerFileName =
            resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
        val feedLogo = imageRepository.get(defaultDrawerFileName) ?: return null
        val feedLogoPath = storageService.getAbsolutePath(feedLogo) ?: return null
        val imageDrawable = feedLogoDrawable ?: withContext(Dispatchers.IO) {
            glide
                .load(feedLogoPath)
                .submit()
                .get()
        }
        feedLogoDrawable = imageDrawable

        // scale factor determined in resources
        val scaleFactor = resources.getFraction(
            R.fraction.nav_button_scale_factor,
            1,
            33
        )
        val logicalWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            imageDrawable.intrinsicWidth.toFloat(),
            resources.displayMetrics
        ) * scaleFactor

        drawerLogoWidth = logicalWidth.toInt()
        tazApiCssDataStore.logoWidth.set(logicalWidth.toInt())

        val logicalHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            imageDrawable.intrinsicHeight.toFloat(),
            resources.displayMetrics
        ) * scaleFactor

        drawerLogoWrapper.updateLayoutParams {
            width = drawerLogoWidth
        }

        drawerLogoWrapper.findViewById<ImageView>(R.id.drawer_logo).apply {
            setImageDrawable(imageDrawable)
            updateLayoutParams<LayoutParams> {
                width = logicalWidth.toInt()
                height = logicalHeight.toInt()
            }
        }
        return imageDrawable
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