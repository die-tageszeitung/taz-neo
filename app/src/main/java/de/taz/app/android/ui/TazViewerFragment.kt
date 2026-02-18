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
    lateinit var drawerViewController: DrawerViewController

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

        viewBinding?.apply {
            drawerViewController = DrawerViewController(
                requireContext(),
                drawerLayout,
                drawerLogoWrapper,
                navView,
                view
            )
            if (enableDrawer) {
                setupDrawer()
            } else {
                drawerLogo.visibility = View.GONE
                drawerLayout.setDrawerLockMode(LOCK_MODE_LOCKED_CLOSED)
            }
        }
    }

    private fun setupDrawer() {
        viewBinding?.apply {

            // Adjust extra padding when we have cutout display
            viewLifecycleOwner.lifecycleScope.launch {
                val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
                if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    navView.setPadding(0, extraPadding, 0, 0)
                }
            }

            drawerViewController.initialize()
            drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    drawerViewController.handleOnDrawerSlider(slideOffset)
                }

                override fun onDrawerOpened(drawerView: View) {
                    drawerAndLogoViewModel.openDrawer()
                }

                override fun onDrawerClosed(drawerView: View) {
                    drawerAndLogoViewModel.closeDrawer()
                }

                override fun onDrawerStateChanged(newState: Int) {}
            })
        }

        // assumes setupDrawer is called from onCreate
        lifecycleScope.launch {
            drawerAndLogoViewModel.drawerState.collect {
                drawerViewController.handleDrawerLogoState(it)
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
        if (drawerAndLogoViewModel.drawerState.value is DrawerState.Open) {
            drawerAndLogoViewModel.closeDrawer()
        }
        super.onDestroy()
    }
}