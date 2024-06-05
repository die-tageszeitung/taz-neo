package de.taz.app.android.ui

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.webkit.WebView
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.coachMarks.TazLogoCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityTazViewerBinding
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.DrawerState
import de.taz.app.android.ui.drawer.DrawerViewController
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * Abstract base class for
 * [de.taz.app.android.ui.issueViewer.IssueViewerActivity] and
 * [de.taz.app.android.ui.bookmarks.BookmarkViewerActivity]
 *
 * This activity creates an instance of [fragmentClass] which is then shown
 *
 */
abstract class TazViewerFragment : ViewBindingFragment<ActivityTazViewerBinding>(), BackFragment {

    abstract val fragmentClass: KClass<out Fragment>

    // Set to false from the child class to disable the drawer e.g. for Bookmarks
    protected open val enableDrawer: Boolean = true

    private lateinit var storageService: StorageService
    private lateinit var imageRepository: ImageRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var drawerViewController: DrawerViewController

    private var navButton: Image? = null
    private var navButtonAlpha = 255f

    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private var viewerFragment: Fragment? = null
    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(requireContext().applicationContext)
        imageRepository = ImageRepository.getInstance(requireContext().applicationContext)
        generalDataStore = GeneralDataStore.getInstance(requireContext().applicationContext)

        // supportFragmentManager recovers state by itself
        if (savedInstanceState == null) {
            viewerFragment = fragmentClass.createInstance()
            childFragmentManager.beginTransaction().add(
                R.id.main_viewer_fragment,
                viewerFragment!!
            ).commit()
        } else {
            viewerFragment = childFragmentManager.fragments.find { it::class == fragmentClass }!!
        }

        if (0 != (requireActivity().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        drawerViewController = DrawerViewController(
            requireContext(),
            viewBinding.drawerLayout,
            viewBinding.drawerLogoWrapper,
            viewBinding.navView,
            drawerAndLogoViewModel::setLogoHiddenState
        )
        if (enableDrawer) {
            setupDrawer()
        } else {
            viewBinding.drawer.visibility = View.GONE
            viewBinding.drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED)
        }
    }

    private fun setupDrawer() {
        viewBinding.apply {
            drawerLogo.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                drawerLayout.updateDrawerLogoBoundingBox(
                    v.width,
                    v.height
                )
            }
            // Somehow this is only applied on opened drawers :shrug:
            drawerLogo.setOnClickListener {
                drawerAndLogoViewModel.closeDrawer()
            }

            // Adjust extra padding when we have cutout display
            viewLifecycleOwner.lifecycleScope.launch {
                val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
                if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    navView.setPadding(0, extraPadding, 0, 0)
                }
            }

            drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    drawerViewController.handleOnDrawerSlider(slideOffset)
                }

                override fun onDrawerOpened(drawerView: View) {
                    drawerAndLogoViewModel.openDrawer()
                    lifecycleScope.launch {
                        TazLogoCoachMark.setFunctionAlreadyDiscovered(requireContext())
                    }
                }

                override fun onDrawerClosed(drawerView: View) {
                    drawerAndLogoViewModel.closeDrawer()
                }

                override fun onDrawerStateChanged(newState: Int) {}
            })
        }

        if (drawerAndLogoViewModel.isLogoHidden()) {
            drawerAndLogoViewModel.hideLogo()
        }

        // assumes setupDrawer is called from onCreate
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    drawerAndLogoViewModel.drawerState.collect {
                        drawerViewController.handleDrawerState(it)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewLifecycleOwner.lifecycleScope.launch {
            val defaultDrawerFileName =
                resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
            imageRepository.get(defaultDrawerFileName)?.let {
                showNavButton(it)
            }
        }
    }

    /**
     * This calculation takes also part in onDrawerSlide.
     * When the fragment is resumed the additional translation needs to be re-applied.
     * This happens here.
     */
    private fun translateDrawerLogo(logoWidth: Float) {
        val drawerWidth = resources.getDimension(R.dimen.drawer_width)
        // Only apply translation if drawer has width match_parent
        // (at the moment this is not the case for sw600 devices)
        if (drawerWidth == resources.getDimension(R.dimen.custom_match_parent)) {
            val drawerMargin =
                resources.getDimension(R.dimen.fragment_drawer_margin_end) / resources.displayMetrics.density
            val translationX =
                resources.getDimension(R.dimen.drawer_logo_translation_x) / resources.displayMetrics.density
            val newTranslationInDp = logoWidth + drawerMargin + translationX

            viewBinding.apply {
                drawerLogoWrapper.translationX = -newTranslationInDp
            }
        }
    }

    private suspend fun showNavButton(navButton: Image) {
        val navButtonPath = storageService.getAbsolutePath(navButton)
        if (this.navButton != navButton && navButtonPath != null) {
            try {
                val imageDrawable = withContext(Dispatchers.IO) {
                    Glide
                        .with(this@TazViewerFragment)
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

                drawerViewController.drawerLogoWidth = logicalWidth.toInt()

                val logicalHeight = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    imageDrawable.intrinsicHeight.toFloat(),
                    resources.displayMetrics
                ) * scaleFactor

                withContext(Dispatchers.Main) {
                    viewBinding.apply {
                        drawerLogo.apply {
                            setImageDrawable(imageDrawable)
                            alpha = navButtonAlpha
                            imageAlpha = (navButton.alpha * 255).toInt()
                            updateLayoutParams<LayoutParams> {
                                width = logicalWidth.toInt()
                                height = logicalHeight.toInt()
                            }
                        }
                        drawerLayout.requestLayout()

                        if (drawerLayout.isOpen) {
                            translateDrawerLogo(logicalWidth)
                        }
                    }
                }
                this.navButton = navButton

                TazLogoCoachMark(this, viewBinding.drawerLogo, imageDrawable)
                    .maybeShow()

            } catch (e: ExecutionException) {
                val hint = "Glide could not get imageDrawable. Probably a SD-Card issue."
                log.error(hint, e)
                SentryWrapper.captureException(e)
                showSdCardIssueDialog()
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (drawerAndLogoViewModel.drawerState.value is DrawerState.Open) {
            drawerAndLogoViewModel.closeDrawer()
            return true
        }
        return (viewerFragment as? BackFragment)?.onBackPressed() ?: false
    }
}