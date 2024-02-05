package de.taz.app.android.ui.webview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.dataStore.TazApiCssDataStore

const val KEY_SCROLL_POSITION = "KEY_SCROLL_POSITION"
const val KEY_SCROLL_POSITION_HORIZONTAL = "KEY_SCROLL_POSITION_HORIZONTAL"

open class WebViewViewModel<DISPLAYABLE : WebViewDisplayable>(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val displayableLiveData = MutableLiveData<DISPLAYABLE?>(null)

    val displayable: DISPLAYABLE?
        get() = displayableLiveData.value

    var scrollPosition: Int?
        get() = savedStateHandle.get(KEY_SCROLL_POSITION)
        set(value) {
            savedStateHandle.set(KEY_SCROLL_POSITION, value)
        }

    // for multi column mode we save the scroll position on x axis:
    var scrollPositionHorizontal: Int?
        get() = savedStateHandle.get(KEY_SCROLL_POSITION_HORIZONTAL)
        set(value) {
            savedStateHandle.set(KEY_SCROLL_POSITION_HORIZONTAL, value)
        }

    val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)
    val nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()
    val fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()
}