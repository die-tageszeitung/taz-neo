package de.taz.app.android.ui.webview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.dataStore.TazApiCssDataStore

const val KEY_SCROLL_POSITION = "KEY_SCROLL_POSITION"

open class WebViewViewModel<DISPLAYABLE : WebViewDisplayable>(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val displayableLiveData = MutableLiveData<DISPLAYABLE?>(null)

    var displayable: DISPLAYABLE?
        get() = displayableLiveData.value
        set(value) {
            displayableLiveData.value = value
        }

    var scrollPosition: Int?
        get() = savedStateHandle.get(KEY_SCROLL_POSITION)
        set(value) {
            savedStateHandle.set(KEY_SCROLL_POSITION, value)
        }

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)
    val nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()
    val fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()
}