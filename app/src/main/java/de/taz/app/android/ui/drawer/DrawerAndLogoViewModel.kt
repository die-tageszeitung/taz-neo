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
 * [DrawerState.isBurger] – whether the logo is the burger icon or not
 * [DrawerState.isHidden] – whether the logo is hidden or not
 * [DrawerState.percentMorphedToBurger] - Float [0,1] indicating how much the logo should be hidden.
 */
class DrawerAndLogoViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _drawerState = MutableStateFlow<DrawerState>(DrawerState.Closed())
    val drawerState = _drawerState.asStateFlow()

    fun setFeedLogo() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(isBurger = false, isHidden = false, percentMorphedToBurger = 0f)
            is DrawerState.Open -> state.copy(isBurger = false, isHidden = false, percentMorphedToBurger = 0f)
        }
    }

    fun setBurgerIcon() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(isBurger = true, isHidden = false, percentMorphedToBurger = 1f)
            is DrawerState.Open -> state.copy(isBurger = true, isHidden = false, percentMorphedToBurger = 1f)
        }
    }

    fun hideLogo() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(isHidden = true)
            is DrawerState.Open -> state.copy(isHidden = true)
        }
    }
    fun setFeedLogoAndHide() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(isBurger = false, isHidden = true, percentMorphedToBurger = 0f)
            is DrawerState.Open -> state.copy(isBurger = false, isHidden = true, percentMorphedToBurger = 0f)
        }
    }

    fun showLogo() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(isHidden = false)
            is DrawerState.Open -> state.copy(isHidden = false)
        }
    }

    fun morphLogoByPercent(percent: Float) {
        val coercedPercent = percent.coerceIn(0f, 1f)
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(percentMorphedToBurger = coercedPercent)
            is DrawerState.Open -> state.copy(percentMorphedToBurger = coercedPercent)
        }
    }

    fun openDrawer() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> DrawerState.Open(state.isBurger, state.isHidden, state.percentMorphedToBurger)
            is DrawerState.Open -> state
        }
    }

    fun closeDrawer() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state
            is DrawerState.Open -> DrawerState.Closed(state.isBurger, state.isHidden, state.percentMorphedToBurger)
        }
    }

    fun isBurgerIcon(): Boolean = when (val state = _drawerState.value) {
        is DrawerState.Closed -> state.isBurger
        is DrawerState.Open -> state.isBurger
    }
}

sealed class DrawerState {
    abstract val isBurger: Boolean
    abstract val isHidden: Boolean
    abstract val percentMorphedToBurger: Float

    data class Open(
        override val isBurger: Boolean = false,
        override val isHidden: Boolean = false,
        override val percentMorphedToBurger: Float = 0f,
    ) :
        DrawerState()

    data class Closed(
        override val isBurger: Boolean = false,
        override val isHidden: Boolean = false,
        override val percentMorphedToBurger: Float = 0f,
    ) :
        DrawerState()
}