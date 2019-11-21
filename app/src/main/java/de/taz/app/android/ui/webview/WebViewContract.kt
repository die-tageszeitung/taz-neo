package de.taz.app.android.ui.webview

import android.view.MenuItem
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseContract

interface WebViewContract: BaseContract {

    interface View<DISPLAYABLE: WebViewDisplayable>: BaseContract.View {

        fun getWebViewDisplayable(): DISPLAYABLE?

        fun setWebViewDisplayable(displayable: DISPLAYABLE?)

        fun getWebView(): AppWebView

        fun isPermanentlyActive(itemId: Int): Boolean

        fun setPermanentlyActive(itemId: Int)

        fun unsetPermanentlyActive(itemId: Int)

        fun loadUrl(url: String)

        fun hideLoadingScreen()

        fun setIconActive(itemId: Int)

        fun setIconInactive(itemId: Int)

        fun shareText(text: String)

    }

    interface Presenter: BaseContract.Presenter, AppWebViewCallback {

        fun onLinkClicked(webViewDisplayable: WebViewDisplayable)

        fun onPageFinishedLoading()

        fun onBackPressed(): Boolean

        fun onBottomNavigationItemClicked(menuItem: MenuItem)
    }

    interface DataController<DISPLAYABLE: WebViewDisplayable> {
        fun getWebViewDisplayable(): DISPLAYABLE?

        fun setWebViewDisplayable(displayable: DISPLAYABLE?)
    }

}