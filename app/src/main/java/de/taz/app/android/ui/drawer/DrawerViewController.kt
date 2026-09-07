package de.taz.app.android.ui.drawer

import android.view.View
import android.widget.ImageView
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * This controller handles the UI updates of the DrawerState collected from the [DrawerAndLogoViewModel]
 * in [TazViewerFragment] and in [PdfPagerFragment].
 * Additionally, it handles the offset in onDrawerSlide of their drawer.
 */
class DrawerViewController(
    private val activity: FragmentActivity,
    private val drawerLayout: DrawerLayout,
    private val drawerLogoWrapper: View,
    private val navView: View,
    private val scope: CoroutineScope
) {

    private val log by Log

    private val generalDataStore = GeneralDataStore.getInstance(activity.applicationContext)

    private val drawerLogo: ImageView = drawerLogoWrapper.findViewById(R.id.drawer_logo)
    private val drawerLogoController = DrawerLogoController(
        activity,
        drawerLogoWrapper,
        drawerLogo,
        navView,
        scope
    ) {
        closeDrawer()
    }

    private var isListDrawer = false

    init {
        scope.launch {
            if (generalDataStore.pdfMode.get()) {
                isListDrawer = BuildConfig.IS_LMD || generalDataStore.useListDrawer.get()
                togglePdfDrawer(isListDrawer)
            }
            // Pre-load the feed logo
            LogoController.getInstance(activity).getFeedDrawable()
        }
    }

    val onBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            closeDrawer()
        }
    }

    suspend fun handleDrawerLogoState(state: DrawerState) {
        log.info("handling DrawerState: $state")

        if (state is DrawerState.Open) {
            activity.onBackPressedDispatcher.addCallback(activity, onBackCallback)
            if (isListDrawer != state.isListDrawer) {
                togglePdfDrawer(state.isListDrawer)
            }
            openDrawer()
            return
        } else {
            onBackCallback.remove()
        }

        drawerLogoController.updateLogoState(state.logoState)
        closeDrawer()
    }

    suspend fun hideLogoWrapper() {
        drawerLogoController.hideLogoWrapper()
    }

    fun showLogoWrapper() {
        drawerLogoController.showLogoWrapper()
    }

    /**
     * Calculate the offsets of the drawerLogo for onDrawerSlide function.
     * The [slideOffset] can be between 0 (closed drawer) and 1 (open drawer).
     */
    fun handleOnDrawerSlider(slideOffset: Float) {
        drawerLogoController.handleOnDrawerSlider(slideOffset)
    }

    fun initialize() {
        drawerLogoController.initialize()
    }

    suspend fun setFeedLogo() {
        drawerLogoController.setFeedLogo()
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

    private fun togglePdfDrawer(showList: Boolean) = scope.launch(Dispatchers.Main) {
        val showListOrLMd = showList || BuildConfig.IS_LMD
        if (generalDataStore.pdfMode.get()) {
            drawerLayout.findViewById<FragmentContainerView>(R.id.fragment_container_view_drawer_body)
                ?.isVisible = !showListOrLMd
            drawerLayout.findViewById<FragmentContainerView>(R.id.fragment_container_view_drawer_body_list)
                ?.isVisible = showListOrLMd
            isListDrawer = showListOrLMd
            generalDataStore.useListDrawer.set(showListOrLMd)
        }
    }
}
