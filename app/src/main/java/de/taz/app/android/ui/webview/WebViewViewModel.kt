package de.taz.app.android.ui.webview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.WebViewDisplayable

abstract class WebViewViewModel<DISPLAYABLE : WebViewDisplayable> : ViewModel() {

    val displayableKeyLiveData = MutableLiveData<String?>(null)
    var  displayableKey
        get() = displayableKeyLiveData.value
        set(value) { displayableKeyLiveData.value = value }

    abstract val displayableLiveData: LiveData<DISPLAYABLE?>
    val displayable: DISPLAYABLE?
        get() = displayableLiveData.value

    var scrollPosition: Int? = null
}