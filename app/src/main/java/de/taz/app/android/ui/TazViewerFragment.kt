package de.taz.app.android.ui

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.coachMarks.TazLogoCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityTazViewerBinding
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.DrawerState
import de.taz.app.android.ui.drawer.DrawerViewController
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * Abstract base class for
 * [de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment] and
 * [de.taz.app.android.ui.bookmarks.BookmarkViewerFragment]
 *
 * This activity creates an instance of [fragmentClass] which is then shown
 *
 */
abstract class TazViewerFragment : ViewBindingFragment<ActivityTazViewerBinding>(), BackFragment {

    abstract val fragmentClass: KClass<out Fragment>

    // Set to false from the child class to disable the drawer e.g. for Bookmarks
    protected open val enableDrawer: Boolean = true

    private lateinit var storageService: StorageService
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var drawerViewController: DrawerViewController

    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private var viewerFragment: Fragment? = null
    private val log by Log

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storageService = StorageService.getInstance(requireContext().applicationContext)
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
            view
        )
        if (enableDrawer) {
            setupDrawer()
        } else {
            viewBinding.drawerLogo.visibility = View.GONE
            viewBinding.drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED)
        }
    }

    private fun setupDrawer() {
        viewBinding.apply {
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

        // assumes setupDrawer is called from onCreate
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val drawerLogo = drawerViewController.setFeedLogo()
                    drawerLogo?.let {
                        TazLogoCoachMark(this@TazViewerFragment, viewBinding.drawerLogo, it)
                            .maybeShow()
                    }
                } catch (e: ExecutionException) {
                    val hint = "Glide could not get imageDrawable. Probably a SD-Card issue."
                    log.error(hint, e)
                    SentryWrapper.captureException(e)
                    showSdCardIssueDialog()
                }
                launch {
                    drawerAndLogoViewModel.drawerState.collect {
                        drawerViewController.handleDrawerLogoState(it)
                    }
                }
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

    override fun onDestroy() {
        // Show the logo, so the state is not with a hidden logo
        drawerAndLogoViewModel.setFeedLogo()
        super.onDestroy()
    }
}