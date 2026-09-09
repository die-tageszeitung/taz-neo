package de.taz.app.android.ui.logo

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnAttach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import de.taz.app.android.LOGO_ANIMATION_DURATION_MS
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.monkey.activityViewModels
import de.taz.app.android.monkey.lifecycleScope
import de.taz.app.android.monkey.withPreviousValue
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.LogoController
import de.taz.app.android.ui.drawer.LogoState
import de.taz.app.android.ui.drawer.NO_TRANSLATION
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.getValue


class LogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.cardview.R.attr.cardViewStyle,
) : CardView(context, attrs, defStyleAttr) {

    private val log by Log
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private val logoController by lazy { LogoController.getInstance(context) }
    private val generalDataStore by lazy { GeneralDataStore.getInstance(context) }

    private val burgerLogo by lazy { findViewById<ImageView>(R.id.burger_logo) }
    private val closeLogo by lazy { findViewById<ImageView>(R.id.close_logo) }
    private val feedLogo by lazy { findViewById<ImageView>(R.id.feed_logo) }

    private lateinit var feedLogoOrBurgerFlow: StateFlow<LogoState>

    private var listenForCloseState: Boolean = false

    private var lastNavView: View? = null
    private var lastSlideOffset: Float = 0f
    private var drawerLogoTranslationX: Float = 0f

    private val currentLogoState: LogoState get() =
        drawerAndLogoViewModel.drawerState.value.logoState

    private var feedWidth: Int = 0
    private var feedHeight: Int = 0
    private var burgerTranslationOffset: Int = 0
    private var burgerTranslationX: Float = 0f
    private var peakTranslationX: Float = 0f

    init {
        context.withStyledAttributes(attrs, R.styleable.LogoView) {
            listenForCloseState = getBoolean(R.styleable.LogoView_listenForCloseState, false)
        }

        inflate(context, R.layout.view_logo, this)

        applyBaseStyle()

        feedLogo.setOnClickListener { drawerAndLogoViewModel.openDrawer() }
        burgerLogo.setOnClickListener { drawerAndLogoViewModel.openDrawer() }
        closeLogo.setOnClickListener { drawerAndLogoViewModel.closeDrawer() }

        doOnAttach {
            updateLayoutParams<MarginLayoutParams> {
                topMargin += resources.getDimensionPixelSize(R.dimen.logo_margin_top)
            }

            val scope = lifecycleScope() ?: return@doOnAttach

            feedLogoOrBurgerFlow = drawerAndLogoViewModel.logoStateFlow
                .filter { it != LogoState.CLOSE && it != LogoState.UNDEFINED }
                .stateIn(scope, SharingStarted.Eagerly, LogoState.FEED)

            scope.launch {
                initDimensions()

                drawerAndLogoViewModel.logoStateFlow
                    .filter { it != LogoState.CLOSE }
                    .withPreviousValue()
                    .collect { (current, previous) ->
                        transitionState((previous ?: LogoState.UNDEFINED) to current)
                    }
            }
        }
    }

    private fun applyBaseStyle() {
        isGone = listenForCloseState
        setCardBackgroundColor(resources.getColor(R.color.drawer_logo_color, context.theme))
        useCompatPadding = false
        preventCornerOverlap = false
        elevation = resources.getDimension(R.dimen.drawer_logo_elevation)
        cardElevation = resources.getDimension(R.dimen.drawer_logo_elevation)
        alpha = 0f
    }

    private suspend fun initDimensions() {
        feedWidth = logoController.getFeedLogoWidth()
        feedHeight = logoController.getFeedLogoHeight()
        burgerTranslationOffset = feedWidth - logoController.burgerWidth

        applyIconProperties(feedLogo, logoController.getFeedDrawable(), feedWidth, feedHeight)
        applyIconProperties(burgerLogo, logoController.burgerDrawable, logoController.burgerWidth, feedHeight)
        applyIconProperties(closeLogo, logoController.closeDrawable, logoController.burgerWidth, feedHeight)

        burgerTranslationX = logoController.burgerWidth.toFloat() - feedWidth
        peakTranslationX = resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden).toFloat() - feedWidth
    }

    private fun applyIconProperties(
        imageView: ImageView,
        drawable: Drawable?,
        width: Int,
        height: Int
    ) {
        if (drawable != null) {
            imageView.setImageDrawable(drawable)
        }
        if (width > 0 && height > 0) {
            imageView.updateLayoutParams {
                this.width = width
                this.height = height
            }
        }
    }

    private suspend fun transitionState(
        transition: Pair<LogoState, LogoState>
    ) {
        val (previous, state) = transition
        if (previous == state) return

        log.debug("from $previous to $state")

        if (listenForCloseState) {
            drawerLogoTranslationX = when (state) {
                LogoState.HIDDEN -> peakTranslationX
                LogoState.CLOSE -> drawerLogoTranslationX
                else -> NO_TRANSLATION
            }
        }

        if (!generalDataStore.animateDrawerLogo.get()) {
            if (state == LogoState.HIDDEN) {
                scrollToPeakThenShowFeedLogo()
            } else {
                showFeedThenScrollIn()
            }
            return
        }

        when (transition) {
            LogoState.UNDEFINED to LogoState.HIDDEN,
            LogoState.FEED to LogoState.HIDDEN ->
                scrollToPeakThenShowFeedLogo()

            LogoState.BURGER to LogoState.HIDDEN ->
                scrollBurgerToPeakThenShowFeedLogo()

            LogoState.UNDEFINED to LogoState.FEED ->
                scrollInThenShowFeed()

            LogoState.HIDDEN to LogoState.FEED -> {
                showFeedThenScrollIn()
            }
            LogoState.BURGER to LogoState.FEED -> {
                feedLogo.isVisible = true
                translationX -= burgerTranslationOffset
                showFeedThenScrollIn()
            }

            LogoState.HIDDEN to LogoState.BURGER ->
                hideFeedThenScrollToBurger()

            LogoState.UNDEFINED to LogoState.BURGER,
            LogoState.FEED to LogoState.BURGER ->
                scrollToBurgerThenHideFeed()

            else -> Unit
        }
    }


    // region drawer
    fun handleOnDrawerSlider(slideOffset: Float, navView: View?) {
        lastSlideOffset = slideOffset
        lastNavView = navView
        isGone = slideOffset == 0f

        val shouldBeClose = if (currentLogoState == LogoState.CLOSE) {
            slideOffset > 0.65f
        } else {
            slideOffset > 0.75f
        }

        if (shouldBeClose && currentLogoState != LogoState.CLOSE) {
            drawerAndLogoViewModel.setCloseButton()
        } else if (!shouldBeClose && currentLogoState == LogoState.CLOSE) {
            when (feedLogoOrBurgerFlow.value) {
                LogoState.BURGER -> drawerAndLogoViewModel.setBurgerIcon()
                LogoState.HIDDEN -> drawerAndLogoViewModel.hideLogo()
                else -> drawerAndLogoViewModel.setFeedLogo()
            }
        }

        closeLogo.isVisible = currentLogoState == LogoState.CLOSE
        burgerLogo.isVisible = currentLogoState == LogoState.BURGER
        feedLogo.isVisible = currentLogoState in listOf(LogoState.FEED, LogoState.HIDDEN)
        translationX = calculateTranslationX(slideOffset, navView)
    }

    private fun calculateTranslationX(slideOffset: Float, navView: View?): Float {
        return logoController.calculateTranslationX(
            slideOffset = slideOffset,
            screenWidth = context.resources.displayMetrics.widthPixels,
            navViewWidth = navView?.width,
            logoWidth = if (currentLogoState == LogoState.BURGER || currentLogoState == LogoState.CLOSE) logoController.burgerWidth else feedWidth,
            effectiveStateForClosedOffset = if (currentLogoState == LogoState.CLOSE) feedLogoOrBurgerFlow.value else currentLogoState,
            drawerLogoTranslationX = drawerLogoTranslationX,
            marginStart = resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_start).toFloat()
        )
    }
    // endregion

    // region animation functions
    private fun scrollToBurgerThenHideFeed() =
        animate()
            .translationX(burgerTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction {
                burgerLogo.isVisible = true
                feedLogo.isVisible = false
                translationX +=burgerTranslationOffset
                alpha = 1f
            }

    private fun showFeedThenScrollIn() =
        animate()
            .translationX(
                resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_start).toFloat()
            ).setDuration(LOGO_ANIMATION_DURATION_MS).withStartAction {
                feedLogo.isVisible = true
                burgerLogo.isVisible = false
            }
            .withEndAction { alpha = 1f }

    private fun scrollInThenShowFeed() =
        animate()
            .translationX(
                resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_start).toFloat()
            ).setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction {
                feedLogo.isVisible = true
                burgerLogo.isVisible = false
                alpha = 1f
            }

    private fun hideFeedThenScrollToBurger() =
        animate()
            .translationX(burgerTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withStartAction {
                feedLogo.alpha = 0f
                burgerLogo.isVisible = true
            }
            .withEndAction {
                feedLogo.isVisible = false
                feedLogo.alpha = 1f
                translationX +=feedWidth - logoController.burgerWidth
                alpha = 1f
            }

    private fun scrollBurgerToPeakThenShowFeedLogo() =
        animate()
            .translationX(peakTranslationX +feedWidth - logoController.burgerWidth)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction {
                translationX -=feedWidth - logoController.burgerWidth
                feedLogo.isVisible = true
                burgerLogo.isVisible = false
                alpha = 1f
            }

    private fun scrollToPeakThenShowFeedLogo() =
        animate()
            .translationX(peakTranslationX)
            .setDuration(LOGO_ANIMATION_DURATION_MS)
            .withEndAction {
                feedLogo.isVisible = true
                burgerLogo.isVisible = false
                alpha = 1f
            }
    // endregion
}
