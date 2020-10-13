package de.taz.app.android.ui.webview

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable

const val KEY_SCROLL_POSITION = "KEY_SCROLL_POSITION"

open class WebViewViewModel<DISPLAYABLE : WebViewDisplayable>(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val displayableLiveData = MutableLiveData<DISPLAYABLE?>(null)

    var issueOperations: IssueOperations? = null

    var displayable: DISPLAYABLE?
        get() = displayableLiveData.value
        set(value) { displayableLiveData.value = value }

    var scrollPosition: Int?
        get() = savedStateHandle.get(KEY_SCROLL_POSITION)
        set(value) { savedStateHandle.set(KEY_SCROLL_POSITION, value) }
}