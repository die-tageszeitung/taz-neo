package de.taz.app.android.ui.webview

import android.view.MenuItem
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseContract

interface WebViewContract : BaseContract {

    interface View<DISPLAYABLE : WebViewDisplayable> : BaseContract.View {

        fun getWebViewDisplayable(): DISPLAYABLE?

        fun setWebViewDisplayable(displayable: DISPLAYABLE?)

        fun getWebView(): AppWebView?

        fun loadUrl(url: String)

        fun hideLoadingScreen()

        fun shareText(text: String)

        fun showFontSettingBottomSheet()

        fun showBookmarkBottomSheet()

    }

    interface Presenter : BaseContract.Presenter, AppWebViewCallback {

        fun onBackPressed(): Boolean

        fun onLinkClicked(webViewDisplayable: WebViewDisplayable)

        fun onPageFinishedLoading()

        fun onBottomNavigationItemClicked(menuItem: MenuItem)
    }

    interface DataController<DISPLAYABLE : WebViewDisplayable> {
        fun getWebViewDisplayable(): DISPLAYABLE?

        fun setWebViewDisplayable(displayable: DISPLAYABLE?)
    }

}