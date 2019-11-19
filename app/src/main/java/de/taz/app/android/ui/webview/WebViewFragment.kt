package de.taz.app.android.ui.webview

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import kotlinx.android.synthetic.main.fragment_webview_section.*


abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable> :
    BaseMainFragment<WebViewPresenter<DISPLAYABLE>>(), WebViewContract.View<DISPLAYABLE>,
    BackFragment {

    override val presenter = WebViewPresenter<DISPLAYABLE>()

    override val inactiveIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark,
        R.id.bottom_navigation_action_share to R.drawable.ic_share,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size
    )

    override val activeIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark_active,
        R.id.bottom_navigation_action_share to R.drawable.ic_share_active,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size_active
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)
    }

    override fun getWebView(): AppWebView {
        return web_view
    }

    override fun hideLoadingScreen() {
        activity?.runOnUiThread {
            web_view_spinner.visibility = View.GONE
            web_view.visibility = View.VISIBLE
        }
    }

    override fun loadUrl(url: String) {
        activity?.runOnUiThread {
            view?.findViewById<WebView>(R.id.web_view)?.loadUrl(url)
        }
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

    override fun onBackPressed(): Boolean {
        return presenter.onBackPressed()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

    override fun shareText(text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

}
