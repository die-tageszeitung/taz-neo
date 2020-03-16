package de.taz.app.android.ui.webview

import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.WebViewDisplayable

abstract class WebViewViewModel<DISPLAYABLE : WebViewDisplayable>: ViewModel() {

    abstract val displayableKey: String?

    var displayable: DISPLAYABLE? = null


}