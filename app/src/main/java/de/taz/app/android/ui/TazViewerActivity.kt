package de.taz.app.android.ui

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.*
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_taz_viewer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

abstract class TazViewerActivity : NightModeActivity(R.layout.activity_taz_viewer) {
    private val log by Log

    abstract val fragmentClass: KClass<out Fragment>

    private lateinit var fileHelper: FileHelper
    private lateinit var imageRepository: ImageRepository
    private lateinit var dataService: DataService
    private lateinit var preferences: SharedPreferences

    private var navButton: Image? = null
    private var navButtonBitmap: Bitmap? = null
    private var navButtonAlpha = 255f

    private val sectionDrawerViewModel: SectionDrawerViewModel by viewModels()

    private lateinit var issueViewerFragment: IssueViewerFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(PREFERENCES_GENERAL, MODE_PRIVATE)
        fileHelper = FileHelper.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)

        issueViewerFragment = fragmentClass.createInstance() as IssueViewerFragment
        supportFragmentManager.beginTransaction().add(
            R.id.main_viewer_fragment,
            issueViewerFragment
        ).commit()

        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var opened = false

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        drawer_logo.translationX = min(
                            slideOffset * (parentView.width - drawerWidth),
                            -5f * resources.displayMetrics.density
                        )
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

        sectionDrawerViewModel.drawerOpen.observe(this) {
            if (it) {
                drawer_layout.openDrawer(GravityCompat.START)
            } else {
                drawer_layout.closeDrawers()
            }
        }


    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launchWhenResumed {
            val timesDrawerShown = preferences.getInt(PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER, 10)
            if (sectionDrawerViewModel.drawerOpen.value == false && timesDrawerShown < DRAWER_SHOW_NUMBER) {
                sectionDrawerViewModel.drawerOpen.value = true
                preferences.edit().apply {
                    putInt(PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER, timesDrawerShown + 1)
                    commit()
                }
            }
        }
        sectionDrawerViewModel.setDefaultDrawerNavButton()
        sectionDrawerViewModel.navButton.observeDistinct(this) {
            if (it != null) {
                lifecycleScope.launch(Dispatchers.IO) { showNavButton(it) }
            }
        }
    }

    private suspend fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            val scaledBitmap = withContext(Dispatchers.IO) {
                dataService.ensureDownloaded(
                    FileEntry(navButton),
                    dataService.getResourceInfo().resourceBaseUrl
                )

                // the scalingFactor is used to scale the image as using 100dp instead of 100px
                // would be too big - the value is taken from experience rather than science
                val scalingFactor = 1f / 3f

                val file = fileHelper.getFile(navButton)
                BitmapFactory.decodeFile(file.absolutePath).let { bitmap ->
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        (TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            bitmap.width.toFloat(),
                            resources.displayMetrics
                        ) * scalingFactor).toInt(),
                        (TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            bitmap.height.toFloat(),
                            resources.displayMetrics
                        ) * scalingFactor).toInt(),
                        false
                    )
                    navButtonBitmap = scaledBitmap
                    navButtonAlpha = navButton.alpha
                    bitmap
                }
            }
            withContext(Dispatchers.Main) {
                findViewById<ImageView>(R.id.drawer_logo)?.apply {
                    background = BitmapDrawable(resources, scaledBitmap)
                    alpha = navButton.alpha
                    imageAlpha = (navButton.alpha * 255).toInt()
                    drawer_layout.updateDrawerLogoBoundingBox(
                        scaledBitmap.width,
                        scaledBitmap.height
                    )
                }
            }
        }
    }


    override fun onBackPressed() {
        if (sectionDrawerViewModel.drawerOpen.value == true) {
            sectionDrawerViewModel.drawerOpen.value = false
            return
        }
        val handled = issueViewerFragment.onBackPressed()
        if (handled) {
            return
        } else {
            super.onBackPressed()
        }
    }
}