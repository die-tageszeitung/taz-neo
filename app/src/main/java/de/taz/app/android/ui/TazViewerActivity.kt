package de.taz.app.android.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.taz.app.android.*
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoKey
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.databinding.ActivityTazViewerBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

const val DRAWER_OVERLAP_OFFSET = -5F

abstract class TazViewerActivity : ViewBindingActivity<ActivityTazViewerBinding>() {

    abstract val fragmentClass: KClass<out Fragment>

    private lateinit var storageService: StorageService
    private lateinit var imageRepository: ImageRepository
    private lateinit var contentService: ContentService
    private lateinit var dataService: DataService

    private var navButton: Image? = null
    private var navButtonAlpha = 255f

    private val sectionDrawerViewModel: SectionDrawerViewModel by viewModels()

    private var viewerFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)

        // supportFragmentManager recovers state by itself
        if (savedInstanceState == null) {
            viewerFragment = fragmentClass.createInstance()
            supportFragmentManager.beginTransaction().add(
                R.id.main_viewer_fragment,
                viewerFragment!!
            ).commit()
        } else {
            viewerFragment = supportFragmentManager.fragments.find { it::class == fragmentClass }!!
        }

        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        viewBinding.apply {
            drawerLogo.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                drawerLayout.updateDrawerLogoBoundingBox(
                    v.width,
                    v.height
                )
            }

            drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                var opened = false

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    (drawerView.parent as? View)?.let { parentView ->
                        val drawerWidth =
                            drawerView.width + (drawerLayout.drawerLogoBoundingBox?.width() ?: 0)
                        if (parentView.width < drawerWidth) {
                            // translation needed for logo to be shown when drawer is too wide:
                            val offsetOnOpenDrawer =
                                slideOffset * (parentView.width - drawerWidth)
                            // translation needed when drawer is closed then:
                            val offsetOnClosedDrawer =
                                (1 - slideOffset) * DRAWER_OVERLAP_OFFSET * resources.displayMetrics.density
                            drawerLogoWrapper.translationX =
                                offsetOnOpenDrawer + offsetOnClosedDrawer
                        }
                    }
                }

                override fun onDrawerOpened(drawerView: View) {
                    opened = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    opened = false
                }

                override fun onDrawerStateChanged(newState: Int) {}
            })

            sectionDrawerViewModel.drawerOpen.observe(this@TazViewerActivity) {
                if (it) {
                    drawerLayout.openDrawer(GravityCompat.START)
                } else {
                    drawerLayout.closeDrawers()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        sectionDrawerViewModel.navButton.observeDistinct(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val resourceInfo = contentService.downloadMetadata(
                    ResourceInfoKey(-1),
                    maxRetries = -1 // Retry indefinitely
                ) as ResourceInfo
                val baseUrl = resourceInfo.resourceBaseUrl
                if (it != null) {
                    contentService.downloadSingleFileIfNotDownloaded(FileEntry(it), baseUrl)
                    showNavButton(it)
                } else {
                    imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)?.let { image ->
                        contentService.downloadSingleFileIfNotDownloaded(FileEntry(image), baseUrl)
                        showNavButton(
                            image
                        )
                    }
                }
            }
        }
    }

    private suspend fun showNavButton(navButton: Image) {
        val navButtonPath = storageService.getAbsolutePath(navButton)
        if (this.navButton != navButton && navButtonPath != null) {
            this.navButton = navButton
            val imageDrawable = withContext(Dispatchers.IO) {
                Glide
                    .with(this@TazViewerActivity)
                    .load(navButtonPath)
                    .submit()
                    .get()
            }

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

            val logicalHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicHeight.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            withContext(Dispatchers.Main) {
                viewBinding.apply {
                    drawerLogo.setImageDrawable(imageDrawable)
                    drawerLogo.alpha = navButtonAlpha
                    drawerLogo.imageAlpha = (navButton.alpha * 255).toInt()
                    drawerLogo.layoutParams.width = logicalWidth.toInt()
                    drawerLogo.layoutParams.height = logicalHeight.toInt()
                    drawerLayout.requestLayout()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (sectionDrawerViewModel.drawerOpen.value == true) {
            sectionDrawerViewModel.drawerOpen.value = false
            return
        }
        val handled = (viewerFragment as? BackFragment)?.onBackPressed() ?: false
        if (handled) {
            return
        } else {
            super.onBackPressed()
        }
    }
}