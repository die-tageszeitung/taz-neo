package de.taz.app.android.ui.webview

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable

open class WebViewViewModel<DISPLAYABLE : WebViewDisplayable> : ViewModel() {

    val displayableLiveData = MutableLiveData<DISPLAYABLE?>(null)

    var issueOperations: IssueOperations? = null

    var displayable: DISPLAYABLE?
        get() = displayableLiveData.value
        set(value) { displayableLiveData.value = value }

    var scrollPosition: Int? = null
}