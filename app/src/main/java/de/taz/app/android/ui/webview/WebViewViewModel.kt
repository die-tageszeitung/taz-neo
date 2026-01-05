package de.taz.app.android.ui.webview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.dataStore.TazApiCssDataStore
import kotlinx.coroutines.flow.MutableStateFlow

const val KEY_SCROLL_POSITION = "KEY_SCROLL_POSITION"
const val KEY_SCROLL_POSITION_HORIZONTAL = "KEY_SCROLL_POSITION_HORIZONTAL"

open class WebViewViewModel<DISPLAYABLE : WebViewDisplayable>(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val displayableFlow = MutableStateFlow<DISPLAYABLE?>(null)

    var displayable: DISPLAYABLE?
        get() = displayableFlow.value
        set(value) {
            displayableFlow.value = value
        }

    var scrollPosition: Int?
        get() = savedStateHandle[KEY_SCROLL_POSITION]
        set(value) {
            savedStateHandle[KEY_SCROLL_POSITION] = value
        }

    // for multi column mode we save the scroll position on x axis:
    var scrollPositionHorizontal: Int?
        get() = savedStateHandle[KEY_SCROLL_POSITION_HORIZONTAL]
        set(value) {
            savedStateHandle[KEY_SCROLL_POSITION_HORIZONTAL] = value
        }

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)
    val nightModeFlow = tazApiCssDataStore.nightMode.asFlow()
    val tapToScrollFlow = tazApiCssDataStore.tapToScroll.asFlow()
    val multiColumnModeFlow = tazApiCssDataStore.multiColumnMode.asFlow()
    val fontSizeFlow = tazApiCssDataStore.fontSize.asFlow()

}