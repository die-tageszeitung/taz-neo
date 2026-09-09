package de.taz.app.android.ui.drawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UNKNOWN = -1
const val NO_TRANSLATION = 0f

/**
 * controller for logo resources and dimensions.
 */
class LogoController private constructor(context: Context) {

    private val log by Log
    private val resources = context.resources
    private val imageRepository = ImageRepository.getInstance(context)
    private val storageService = StorageService.getInstance(context)
    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(context)
    private val glide = Glide.with(context)

    val burgerDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_burger_menu, null)
    val closeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_close_drawer, null)

    val burgerWidth = resources.getDimensionPixelSize(R.dimen.drawer_burger_menu_width)

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
            tazApiCssDataStore.logoWidth.set(_feedLogoWidth)
        }
        return _feedLogoWidth
    }

    /**
     * Pure math helper to calculate the logo's X position during drawer sliding.
     */
    fun calculateTranslationX(
        slideOffset: Float,
        screenWidth: Int,
        navViewWidth: Int?,
        logoWidth: Int,
        effectiveStateForClosedOffset: LogoState,
        drawerLogoTranslationX: Float,
        marginStart: Float
    ): Float {
        val logoTranslationForClosedDrawer = (if (effectiveStateForClosedOffset == LogoState.BURGER ||
            effectiveStateForClosedOffset == LogoState.HIDDEN
        ) 0f else marginStart) + drawerLogoTranslationX

        val logoTranslationForOpenDrawer = if (navViewWidth != null && logoWidth + navViewWidth > screenWidth) {
            (screenWidth - navViewWidth - logoWidth).toFloat()
        } else {
            marginStart
        }

        return slideOffset * logoTranslationForOpenDrawer + (1 - slideOffset) * logoTranslationForClosedDrawer
    }
}
