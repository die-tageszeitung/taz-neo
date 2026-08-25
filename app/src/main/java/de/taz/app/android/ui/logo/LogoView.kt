package de.taz.app.android.ui.logo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.view.doOnLayout
import de.taz.app.android.LOGO_ANIMATION_DURATION_MS
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.drawer.LogoController
import de.taz.app.android.ui.drawer.LogoState
import de.taz.app.android.ui.drawer.NO_TRANSLATION
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt


class LogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.cardview.R.attr.cardViewStyle,
) : CardView(context, attrs, defStyleAttr) {

    private val log by Log

    private val logoController by lazy { LogoController.getInstance(context) }
    private val generalDataStore by lazy { GeneralDataStore.getInstance(context) }

    private val burgerLogo by lazy { findViewById<ImageView>(R.id.burger_logo) }
    private val feedLogo by lazy { findViewById<ImageView>(R.id.feed_logo) }

    private val drawerLogoMarginStart by lazy {
        resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_start).toFloat()
    }

    // region views
    init {
        // Inflate the layout first, so that we can find the views below
        inflate(context, R.layout.view_logo, this)
        setCardBackgroundColor(resources.getColor(R.color.drawer_logo_color, context.theme))
        useCompatPadding = false
        preventCornerOverlap = false
        elevation = resources.getDimension(R.dimen.drawer_logo_elevation)
        cardElevation = resources.getDimension(R.dimen.drawer_logo_elevation)

        // hide until initialized
        alpha = 0f

        CoroutineScope(Dispatchers.Main).launch {
            setTranslationY()
            logoController.ensureFeedLogo(feedLogo)
            logoController.ensureBurgerIcon(burgerLogo)
            requestLayout()
        }
    }

    suspend fun transitionState(
        transition: Pair<LogoState, LogoState>,
    ) {
        val (previousState, logoState) = transition
        log.debug("from state $previousState to $logoState")

        // do not animate
        if(!generalDataStore.animateDrawerLogo.get()) {
            if (logoState == LogoState.HIDDEN)  {
                scrollToPeakThenShowFeedLogo()
            } else {
                showFeedThenScrollIn()
            }
            return
        }

        when (transition) {
            LogoState.UNDEFINED to LogoState.HIDDEN -> {
                doOnLayout {
                    scrollToPeakThenShowFeedLogo()
                }
            }
            LogoState.UNDEFINED to LogoState.FEED -> {
                scrollInThenShowFeed()
            }
            LogoState.UNDEFINED to LogoState.BURGER -> {
                scrollToBurgerThenHideFeed()
            }
            LogoState.FEED to LogoState.BURGER -> {
                scrollToBurgerThenHideFeed()
            }

            LogoState.HIDDEN to LogoState.BURGER -> {
                hideFeedThenScrollToBurger()
            }

            LogoState.HIDDEN to LogoState.FEED,
            LogoState.BURGER to LogoState.FEED -> {
                showFeedThenScrollIn()
            }

            LogoState.BURGER to LogoState.HIDDEN,
            LogoState.FEED to LogoState.HIDDEN -> {
                scrollToPeakThenShowFeedLogo()
            }

            else -> Unit
        }

    }

    private val feedLogoWidth by lazy { runBlocking { logoController.getFeedLogoWidth().toFloat() }}
    private val feedTranslationX by lazy { NO_TRANSLATION + drawerLogoMarginStart }
    private val burgerTranslationX by lazy {
        logoController.burgerWidthFromDimens - feedLogoWidth
    }
    private val peakTranslationX by lazy {
        resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden) - feedLogoWidth
    }

    private fun scrollToBurgerThenHideFeed() {
        animate()
            .translationX(burgerTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction {
                feedLogo.alpha = 0f
                alpha = 1f
            }
            .withLayer()
    }

    private fun showFeedThenScrollIn() {
        animate()
            .translationX(feedTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withStartAction { feedLogo.alpha = 1f }
            .withEndAction { alpha = 1f }
            .withLayer()
    }

    private fun scrollInThenShowFeed() {
        animate()
            .translationX(feedTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction{
                feedLogo.alpha = 1f
                alpha = 1f
            }
            .withLayer()
    }

    private fun hideFeedThenScrollToBurger() {
        animate()
            .translationX(burgerTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withStartAction { feedLogo.alpha = 0f }
            .withEndAction { alpha = 1f }
            .withLayer()
    }

    private fun scrollToPeakThenShowFeedLogo() {
        animate()
            .translationX(peakTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction {
                feedLogo.alpha = 1f
                alpha = 1f
            }
            .withLayer()
    }

    private val baseTranslation by lazy {
        resources.getDimension(R.dimen.drawer_logo_translation_y)
    }

    private suspend fun setTranslationY() {
        // Adjust padding when we have cut out display

        val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
        val extraPaddingFloat =
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                extraPadding.toFloat()
            } else {
                0f
            }
        translationY = baseTranslation + extraPaddingFloat
    }
}