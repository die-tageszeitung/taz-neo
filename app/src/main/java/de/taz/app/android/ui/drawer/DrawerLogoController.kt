package de.taz.app.android.ui.drawer

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles drawer-specific logo behavior, including sliding animations and icon state transitions.
 */
class DrawerLogoController(
    private val context: Context,
    private val drawerLogoWrapper: View,
    private val drawerLogo: ImageView,
    private val navView: View,
    private val scope: CoroutineScope,
    private val onCloseClick: () -> Unit
) {
    private val log by Log
    private val logoController = LogoController.getInstance(context)

    private var currentLogoState: LogoState = LogoState.UNDEFINED
    private var feedLogoOrBurger: LogoState = LogoState.UNDEFINED
    private var drawerLogoTranslationX: Float = 0f

    fun initialize() {
        handleOnDrawerSlider(0f)
    }

    suspend fun updateLogoState(logoState: LogoState) {
        if (logoState == LogoState.HIDDEN) {
            // TODO setFeedLogo()
            hideLogoWrapper()
        } else {
            showLogoWrapper()
        }

        when (logoState) {
            LogoState.FEED -> setFeedLogo()
            LogoState.BURGER -> setBurgerIcon()
            LogoState.CLOSE -> setCloseIcon()
            else -> Unit
        }
    }

    fun hideLogoWrapper() {
        val transX = logoController.calculateHiddenTranslationX(drawerLogoWrapper)
        if (transX != drawerLogoWrapper.translationX) {
            drawerLogoTranslationX = transX
            drawerLogoWrapper.translationX = transX
        }
    }

    fun showLogoWrapper() {
        drawerLogoWrapper.translationX = NO_TRANSLATION
        drawerLogoTranslationX = NO_TRANSLATION
    }

    fun handleOnDrawerSlider(slideOffset: Float) {
        drawerLogoWrapper.alpha = if (slideOffset == 0f) 0f else 1f

        val translationX = calculateTranslationXOnDrawerSlide(slideOffset)
        drawerLogoWrapper.translationX = translationX

        // Decide on icon:
        val shouldBeClose = (slideOffset > 0.7)
        if ((shouldBeClose) && (currentLogoState != LogoState.CLOSE)) {
            scope.launch { setCloseIcon() }
        } else if (!shouldBeClose && currentLogoState == LogoState.CLOSE) {
            scope.launch {
                if (feedLogoOrBurger == LogoState.BURGER) {
                    setBurgerIcon()
                } else {
                    setFeedLogo()
                }
            }
        }
    }

    private fun calculateTranslationXOnDrawerSlide(slideOffset: Float): Float {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val logoTranslationForClosedDrawer: Int =
            if (currentLogoState == LogoState.BURGER) {
                NO_TRANSLATION.toInt()
            } else {
                NO_TRANSLATION.toInt() + logoController.drawerLogoMarginStart
            } + drawerLogoTranslationX.toInt()
        val drawerWidthLogoBiggerThanScreenWidth =
            drawerLogoWrapper.width + navView.width > screenWidth

        val logoTranslationForOpenDrawer = if (drawerWidthLogoBiggerThanScreenWidth) {
            screenWidth - navView.width - drawerLogoWrapper.width
        } else {
            logoController.drawerLogoMarginStart
        }

        val offsetOnOpenDrawer = slideOffset * logoTranslationForOpenDrawer
        val offsetOnClosedDrawer = (1 - slideOffset) * logoTranslationForClosedDrawer
        return offsetOnOpenDrawer + offsetOnClosedDrawer
    }

    private suspend fun setBurgerIcon() {
        feedLogoOrBurger = LogoState.BURGER
        if (currentLogoState == LogoState.BURGER) return

        log.debug("setBurgerLogo")
        currentLogoState = LogoState.BURGER
        logoController.ensureBurgerIcon(drawerLogoWrapper, drawerLogo)
    }

    private suspend fun setCloseIcon() {
        if (currentLogoState == LogoState.CLOSE) return
        log.debug("setCloseIcon")
        currentLogoState = LogoState.CLOSE
        val feedHeight = logoController.getFeedLogoHeight()
        drawerLogoWrapper.updateLayoutParams {
            width = logoController.burgerWidthFromDimens
            height = feedHeight
        }
        logoController.ensureIconAndSize(
            drawerLogo,
            logoController.burgerWidthFromDimens,
            feedHeight,
            logoController.closeDrawable,
            NO_TRANSLATION
        )
        drawerLogo.setOnClickListener {
            onCloseClick()
        }
    }

    suspend fun setFeedLogo() {
        feedLogoOrBurger = LogoState.FEED
        if (currentLogoState == LogoState.FEED) return
        log.debug("setFeedLogo")
        currentLogoState = LogoState.FEED

        val width = logoController.getFeedLogoWidth()
        withContext(Dispatchers.Main) {
            logoController.ensureFeedLogo(drawerLogo)
            drawerLogoWrapper.updateLayoutParams {
                this.width = width
            }
        }
    }
}
