package de.taz.app.android.ui.drawer

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UNKNOWN = -1
const val NO_TRANSLATION = 0f

/**
 * Shared controller for logo resources, dimensions, and basic UI updates.
 * Used by [DrawerLogoController] and [de.taz.app.android.ui.logo.LogoView].
 */
class LogoController private constructor(context: Context) {

    private val log by Log
    private val resources = context.resources
    private val imageRepository = ImageRepository.getInstance(context)
    private val storageService = StorageService.getInstance(context)
    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(context)
    private val generalDataStore = GeneralDataStore.getInstance(context)
    private val glide = Glide.with(context)

    val burgerDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_burger_menu, null)
    val closeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_close_drawer, null)

    val burgerWidthFromDimens = resources.getDimensionPixelSize(R.dimen.drawer_burger_menu_width)
    val drawerLogoMarginStart = resources.getDimensionPixelSize(R.dimen.drawer_logo_margin_start)
    val drawerLogoPeakWhenHidden = resources.getDimensionPixelSize(R.dimen.drawer_logo_peak_when_hidden)

    private var _feedLogoDrawable: Drawable? = null
    private var _feedLogoHeight = UNKNOWN
    private var _feedLogoWidth = UNKNOWN
    private var _scaleFactor = UNKNOWN.toFloat()

    companion object {
        @Volatile
        private var instance: LogoController? = null

        fun getInstance(context: Context): LogoController {
            return instance ?: synchronized(this) {
                instance ?: LogoController(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun getFeedDrawable(): Drawable? {
        _feedLogoDrawable?.let { return it }

        val defaultDrawerFileName = resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
        val feedLogo = imageRepository.get(defaultDrawerFileName) ?: return null
        val feedLogoPath = storageService.getAbsolutePath(feedLogo) ?: return null

        _feedLogoDrawable = withContext(Dispatchers.IO) {
            try {
                glide.load(feedLogoPath).submit().get()
            } catch (e: Exception) {
                log.error("Failed to load feed logo", e)
                null
            }
        }
        return _feedLogoDrawable
    }

    private fun getScaleFactor(): Float {
        if (_scaleFactor == UNKNOWN.toFloat()) {
            _scaleFactor = resources.getFraction(R.fraction.nav_button_scale_factor, 1, 33)
        }
        return _scaleFactor
    }

    suspend fun getFeedLogoHeight(): Int {
        if (_feedLogoHeight == UNKNOWN) {
            val imageDrawable = getFeedDrawable() ?: return UNKNOWN
            _feedLogoHeight = (TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicHeight.toFloat(),
                resources.displayMetrics
            ) * getScaleFactor()).toInt()
        }
        return _feedLogoHeight
    }

    suspend fun getFeedLogoWidth(): Int {
        if (_feedLogoWidth == UNKNOWN) {
            val imageDrawable = getFeedDrawable() ?: return UNKNOWN
            _feedLogoWidth = (TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicWidth.toFloat(),
                resources.displayMetrics
            ) * getScaleFactor()).toInt()
        }
        return _feedLogoWidth
    }

    /* TODO simplify? split up? */
    fun ensureIconAndSize(
        imageView: ImageView,
        newWidth: Int,
        newHeight: Int,
        drawable: Drawable?,
        translationX: Float? = null
    ) {
        if ((drawable != null) && (imageView.drawable != drawable)) {
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

    suspend fun ensureFeedLogo(imageView: ImageView) {
        val drawable = getFeedDrawable() ?: return
        val newHeight = getFeedLogoHeight()
        val newWidth = getFeedLogoWidth()

        if (newHeight == UNKNOWN || newWidth == UNKNOWN) {
            return
        }

        ensureIconAndSize(imageView, newWidth, newHeight, drawable)
        tazApiCssDataStore.logoWidth.set(newWidth)
    }

    suspend fun ensureBurgerIcon(imageView: ImageView) {
        ensureIconAndSize(
            imageView,
            burgerWidthFromDimens,
            getFeedLogoHeight(),
            burgerDrawable,
            NO_TRANSLATION
        )
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

    /**
     * Calculates the translationX needed to hide the logo wrapper, leaving only a "peak" visible.
     */
    fun calculateHiddenTranslationX(wrapperView: View): Float {
        val width = if (wrapperView.measuredWidth > 0) {
            wrapperView.measuredWidth.toFloat()
        } else {
            // Fallback to burger width if not measured yet
            burgerWidthFromDimens.toFloat()
        }
        return -width + drawerLogoPeakWhenHidden
    }

    suspend fun setupLogos(
        burgerLogo: ImageView,
        burgerWrapper: View,
        feedLogo: ImageView? = null,
        onClick: () -> Unit
    ) {
        feedLogo?.let { ensureFeedLogo(it) }
        ensureBurgerIcon(burgerWrapper, burgerLogo)

        // Adjust padding when we have cut out display
        val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
        if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val extraPaddingFloat = extraPadding.toFloat()
            val baseTranslation = resources.getDimension(R.dimen.drawer_logo_translation_y)
            feedLogo?.translationY = baseTranslation + extraPaddingFloat
            burgerWrapper.translationY = baseTranslation + extraPaddingFloat
        }

        feedLogo?.setOnClickListener { onClick() }
        burgerLogo.setOnClickListener { onClick() }
    }
}
