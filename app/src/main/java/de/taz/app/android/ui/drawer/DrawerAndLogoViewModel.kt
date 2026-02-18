package de.taz.app.android.ui.drawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.taz.app.android.util.Log
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
            is DrawerState.Closed -> state.copy(logoState = LogoState.FEED)
            is DrawerState.Open -> state.copy(logoState = LogoState.FEED)
        }
    }

    fun setBurgerIcon() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(logoState = LogoState.BURGER)
            is DrawerState.Open -> state.copy(logoState = LogoState.BURGER)
        }
    }

    fun hideLogo() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(logoState = LogoState.HIDDEN)
            is DrawerState.Open -> state.copy(logoState = LogoState.HIDDEN)
        }
    }

    fun openDrawer() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> DrawerState.Open(state.isListDrawer, state.logoState)
            is DrawerState.Open -> state
        }
    }

    fun closeDrawer() {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state
            is DrawerState.Open -> DrawerState.Closed(state.isListDrawer, state.logoState)
        }
    }

    fun isBurgerIcon(): Boolean {
        return _drawerState.value.logoState == LogoState.BURGER
    }

    fun setNewDrawer(isNew: Boolean) {
        _drawerState.value = when (val state = _drawerState.value) {
            is DrawerState.Closed -> state.copy(isListDrawer = isNew)
            is DrawerState.Open -> state.copy(isListDrawer = isNew)
        }
    }
}

sealed class DrawerState {
    abstract val isListDrawer: Boolean
    abstract val logoState: LogoState

    data class Open(
        override val isListDrawer: Boolean = false,
        override val logoState: LogoState = LogoState.FEED,
    ) :
        DrawerState()

    data class Closed(
        override val isListDrawer: Boolean = false,
        override val logoState: LogoState = LogoState.FEED
    ) :
        DrawerState()
}

enum class LogoState {
    BURGER, CLOSE, FEED, HIDDEN
}
