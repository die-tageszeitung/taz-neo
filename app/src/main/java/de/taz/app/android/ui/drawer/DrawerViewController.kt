package de.taz.app.android.ui.drawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentContainerView
import com.bumptech.glide.Glide
import de.taz.app.android.BuildConfig
import de.taz.app.android.HIDE_LOGO_DELAY_MS
import de.taz.app.android.LOGO_ANIMATION_DURATION_MS
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNKNOWN = -1
private const val NO_TRANSLATION = 0f

/**
 * This controller handles the UI updates of the DrawerState collected from the [DrawerAndLogoViewModel]
 * in [TazViewerFragment] and in [PdfPagerFragment].
 * Additionally, it handles the offset in onDrawerSlide of their drawer.
 */
class DrawerViewController(
    context: Context,
    private val drawerLayout: DrawerLayout,
    private val drawerLogoWrapper: View,
    private val navView: View,
    private val rootView: View,
) {

    private val log by Log

    private val resources = context.resources
    private val imageRepository = ImageRepository.getInstance(context)
    private val storageService = StorageService.getInstance(context)
    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(context)
    private val generalDataStore = GeneralDataStore.getInstance(context)
    private val glide = Glide.with(context)

    private var wasHidden = false

    private var isLogoBurger = false
    private var isLogoClose = false

    private val burgerDrawable =
        ResourcesCompat.getDrawable(resources, R.drawable.ic_burger_menu, null)
    private val closeDrawable =
        ResourcesCompat.getDrawable(resources, R.drawable.ic_close_drawer, null)

    private val burgerWidthFromDimens =
        resources.getDimensionPixelSize(R.dimen.drawer_burger_menu_width)

    private val drawerTranslationX = resources.getDimensionPixelSize(R.dimen.drawer_logo_translation_x)
    private val drawerLogoPeak = resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden)

    private var isListDrawer = false

    init {
        CoroutineScope(Dispatchers.Default).launch {
            if (!BuildConfig.IS_LMD && generalDataStore.pdfMode.get()) {
                isListDrawer = generalDataStore.useListDrawer.get()
                togglePdfDrawer(isListDrawer)
            }
        }
    }

    suspend fun handleDrawerLogoState(state: DrawerState) {
        log.info("handling DrawerState: ${state}")

        if (state is DrawerState.Open) {
            if (isListDrawer != state.isListDrawer) {
                togglePdfDrawer(state.isListDrawer)
            }
            openDrawer()
            return
        }

        if (wasHidden) {
            showDrawerLogoAnimated(state)
        }

        when (state.logoState) {
            LogoState.FEED ->
                setFeedLogo()

            LogoState.BURGER ->
                setBurgerIcon()

            LogoState.CLOSE ->
                setCloseIcon()

            LogoState.HIDDEN ->
                hideDrawerLogoAnimatedWithDelay()
        }
        closeDrawer()

    }


    /**
     * Calculate the offsets of the drawerLogo for onDrawerSlide function.
     * The [slideOffset] can be between 0 (closed drawer) and 1 (open drawer).
     */
    fun handleOnDrawerSlider(slideOffset: Float) = CoroutineScope(Dispatchers.Main).launch {
        // Decide on icon:
        if (slideOffset > 0.5) {
            setCloseIcon()
        } else {
            if (isLogoBurger) {
                setBurgerIcon()
            } else {
                setFeedLogo()
            }
        }

        if (slideOffset == 0f) {
            drawerLogoWrapper.alpha = 0f
        } else {
            drawerLogoWrapper.alpha = 1f
        }

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
        val logoTranslationForClosedDrawer = if (isLogoBurger) -drawerTranslationX else NO_TRANSLATION.toInt()
        val drawerWidthLogoBiggerThenScreenWidth =
            drawerLogoWrapper.width + navView.width > screenWidth

        val logoTranslationForOpenDrawer = if (drawerWidthLogoBiggerThenScreenWidth) {
            screenWidth - navView.width - drawerLogoWrapper.width - drawerTranslationX
        } else {
           drawerTranslationX
        }
        // translation needed for logo when drawer is open (slideOffset = 1) with logo too wide:
        val offsetOnOpenDrawer = slideOffset * logoTranslationForOpenDrawer

        // translation needed when drawer is closed (slideOffset = 0):
        val offsetOnClosedDrawer = (1 - slideOffset) * logoTranslationForClosedDrawer
        return offsetOnOpenDrawer + offsetOnClosedDrawer
    }

    private suspend fun hideDrawerLogoAnimatedWithDelay() {
        val hideList = listOf(R.id.feed_logo, R.id.burger_wrapper)

        val transX = -getFeedLogoWidth().toFloat() + drawerLogoPeak

        hideList.forEach { idToHide ->
            val viewToHide = rootView.findViewById<View>(idToHide)
            if (transX != viewToHide.translationX) {
                viewToHide.animate()
                    .setDuration(LOGO_ANIMATION_DURATION_MS)
                    .setStartDelay(HIDE_LOGO_DELAY_MS)
                    .translationX(transX)
                    .setInterpolator(AccelerateDecelerateInterpolator())
            }
        }
        wasHidden = true
    }

    private fun showDrawerLogoAnimated(state: DrawerState) {
        val hideList = if(state.logoState == LogoState.FEED) {
            listOf(R.id.feed_logo, R.id.burger_wrapper)
        } else {
            listOf(R.id.burger_wrapper)
        }

        hideList.forEach { idToHide ->
            val viewToHide = rootView.findViewById<View>(idToHide)
            viewToHide.animate()
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(0L)
                .translationX(NO_TRANSLATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
        }
        wasHidden = false
    }

    private suspend fun setBurgerIcon() {
        if (isLogoBurger && !isLogoClose) {
            return
        }

        log.info("setBurgerLogo")
        val imageView = drawerLogoWrapper.findViewById<ImageView>(R.id.drawer_logo)

        isLogoBurger = true
        isLogoClose = false

        ensureBurgerIcon(drawerLogoWrapper, imageView)
    }

    suspend fun ensureBurgerIcon(wrapperView: View, imageView: ImageView) {
        wrapperView.updateLayoutParams {
            width = burgerWidthFromDimens
        }
        ensureIconAndSize(
            imageView,
            burgerWidthFromDimens,
            getFeedLogoHeight(),
            burgerDrawable,
            NO_TRANSLATION
        )
    }

    private fun ensureIconAndSize(
        imageView: ImageView,
        newWidth: Int,
        newHeight: Int,
        drawable: Drawable?,
        translationX: Float? = null
    ) {
        if (drawable != null && imageView.drawable != drawable) {
            imageView.setImageDrawable(drawable)
        }
        if (imageView.height != newHeight || imageView.width != newWidth) {
            imageView.updateLayoutParams {
                height = newHeight
                width = newWidth
            }
        }
        if (translationX != null) {
            imageView.translationX = translationX
        }
    }

    private suspend fun setCloseIcon() {
        log.debug("setCloseIcon")
        isLogoClose = true
        drawerLogoWrapper.updateLayoutParams {
            width = burgerWidthFromDimens
            height = getFeedLogoHeight()
        }
        drawerLogoWrapper.findViewById<ImageView>(R.id.drawer_logo).apply {
            updateLayoutParams {
                width = burgerWidthFromDimens
                height = getFeedLogoHeight()
            }
            setImageDrawable(closeDrawable)
            setOnClickListener {
                closeDrawer()
            }
        }
    }

    fun initialize() {
        handleOnDrawerSlider(0f)
    }

    suspend fun setFeedLogo() {
        log.info("setFeedLogo")
        isLogoBurger = false
        isLogoClose = false

        val logo = drawerLogoWrapper.findViewById<ImageView>(R.id.drawer_logo)

        if (logo.drawable == getFeedDrawable())
            return

        log.info("setFeedLogo - drawable changed")
        withContext(Dispatchers.Main) {
            ensureFeedLogo(logo)
            drawerLogoWrapper.updateLayoutParams {
                width = getFeedLogoWidth()
            }
        }
        tazApiCssDataStore.logoWidth.set(getFeedLogoWidth())
    }

    suspend fun ensureFeedLogo(imageView: ImageView) {
        getFeedDrawable() ?: return
        val newHeight = getFeedLogoHeight()
        val newWidth = getFeedLogoWidth()

        if (newHeight == UNKNOWN || newWidth == UNKNOWN) {
            return
        }

        ensureIconAndSize(imageView, newWidth, newHeight, getFeedDrawable())
    }

    private var _feedLogoDrawable: Drawable? = null
    private suspend fun getFeedDrawable(): Drawable? {
        // if we already have it - return it
        _feedLogoDrawable?.let { return it }

        // else get it from database
        val defaultDrawerFileName =
            resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
        val feedLogo = imageRepository.get(defaultDrawerFileName) ?: return null
        val feedLogoPath = storageService.getAbsolutePath(feedLogo) ?: return null

        _feedLogoDrawable = withContext(Dispatchers.IO) {
            glide
                .load(feedLogoPath)
                .submit()
                .get()
        }
        return _feedLogoDrawable
    }

    private var _feedLogoHeight = UNKNOWN
    private suspend fun getFeedLogoHeight(): Int {
        if (_feedLogoHeight == UNKNOWN) {
            val imageDrawable = getFeedDrawable() ?: return UNKNOWN

            // scale factor determined in resources
            val scaleFactor = resources.getFraction(
                R.fraction.nav_button_scale_factor,
                1,
                33
            )
            _feedLogoHeight = (TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicHeight.toFloat(),
                resources.displayMetrics
            ) * scaleFactor).toInt()
        }
        return _feedLogoHeight
    }

    private var _feedLogoWidth = UNKNOWN
    private suspend fun getFeedLogoWidth(): Int {
        if (_feedLogoWidth == UNKNOWN) {
            val imageDrawable = getFeedDrawable() ?: return UNKNOWN

            // scale factor determined in resources
            val scaleFactor = resources.getFraction(
                R.fraction.nav_button_scale_factor,
                1,
                33
            )
            _feedLogoWidth = (TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicWidth.toFloat(),
                resources.displayMetrics
            ) * scaleFactor).toInt()
        }
        return _feedLogoWidth
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

    private fun togglePdfDrawer(showList: Boolean) = CoroutineScope(Dispatchers.Main).launch {
        if (!BuildConfig.IS_LMD && generalDataStore.pdfMode.get()) {
            drawerLayout.findViewById<FragmentContainerView>(R.id.fragment_container_view_drawer_body)
                ?.isVisible = !showList
            drawerLayout.findViewById<FragmentContainerView>(R.id.fragment_container_view_drawer_body_list)
                ?.isVisible = showList
            isListDrawer = showList
            generalDataStore.useListDrawer.set(showList)
        }
    }
}