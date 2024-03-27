package de.taz.app.android.ui.webview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TapIconsViewModel : ViewModel() {

    private val _showTapIconsFlow = MutableStateFlow(false)
    val showTapIconsFlow: StateFlow<Boolean> = _showTapIconsFlow.asStateFlow()

    fun showTapIcons() {
        _showTapIconsFlow.value = true
    }

    fun hideTapIcons() {
        _showTapIconsFlow.value = false
    }
}
