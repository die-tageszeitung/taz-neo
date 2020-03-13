package de.taz.app.android.ui.webview

import androidx.lifecycle.ViewModel
import de.taz.app.android.api.interfaces.WebViewDisplayable

class WebViewViewModel<DISPLAYABLE : WebViewDisplayable>: ViewModel() {

    var displayableKey: String = ""

    var displayable: DISPLAYABLE? = null


}