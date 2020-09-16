package de.taz.app.android.ui.webview

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable

open class WebViewViewModel<DISPLAYABLE : WebViewDisplayable> : ViewModel() {

    private val mutableDisplayableLiveData = MutableLiveData<DISPLAYABLE?>(null)

    var issueOperations: IssueOperations? = null

    var displayable: DISPLAYABLE?
        get() = mutableDisplayableLiveData.value
        set(value) { mutableDisplayableLiveData.value = value }

    var scrollPosition: Int? = null
}