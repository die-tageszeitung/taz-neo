package de.taz.app.android.ui.drawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This View Model should handle the sates of the drawer and the logo.
 * The corresponding Viewer Fragments (eg [TazViewerFragment]) can listen to the states.
 * [DrawerState] holds following properties:
 * [DrawerState.Open] - the drawer is opened
 * [DrawerState.Closed] - the drawer is closed
 * [DrawerState.hideLogo] – whether the logo is hidden or not
 * [DrawerState.percentHide] - Float [0,1] indicating how much the logo should be hidden.
 */
class DrawerAndLogoViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _drawerState = MutableStateFlow<DrawerState>(DrawerState.Closed())
    val drawerState = _drawerState.asStateFlow()

    fun showLogo() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(hideLogo = false, percentHide = 0f)
            is DrawerState.Open -> state.copy(hideLogo = false, percentHide = 0f)
        }
    }

    fun hideLogo() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(hideLogo = true, percentHide = 1f)
            is DrawerState.Open -> state.copy(hideLogo = true, percentHide = 1f)
        }
    }

    fun setLogoHiddenState(isHidden: Boolean) {
        if (isHidden) {
            hideLogo()
        } else {
            showLogo()
        }
    }

    fun hideLogoByPercent(percent: Float) {
        val coercedPercent = percent.coerceIn(0f, 1f)
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(percentHide = coercedPercent)
            is DrawerState.Open -> state.copy(percentHide = coercedPercent)
        }
    }

    fun openDrawer() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> DrawerState.Open(state.hideLogo, state.percentHide)
            is DrawerState.Open -> state
        }
    }

    fun closeDrawer() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state
            is DrawerState.Open -> DrawerState.Closed(state.hideLogo, state.percentHide)
        }
    }

    fun isLogoHidden(): Boolean = when (val state = _drawerState.value) {
        is DrawerState.Closed -> state.hideLogo
        is DrawerState.Open -> state.hideLogo
    }
}

sealed class DrawerState {
    abstract val hideLogo: Boolean
    abstract val percentHide: Float

    data class Open(
        override val hideLogo: Boolean = false,
        override val percentHide: Float = 0f,
    ) :
        DrawerState()

    data class Closed(
        override val hideLogo: Boolean = false,
        override val percentHide: Float = 0f,
    ) :
        DrawerState()
}