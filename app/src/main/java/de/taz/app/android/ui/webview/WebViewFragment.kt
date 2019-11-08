package de.taz.app.android.ui.webview

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import kotlinx.android.synthetic.main.fragment_webview.*


abstract class WebViewFragment(
    private val _webViewDisplayable: WebViewDisplayable? = null
) : WebViewBaseFragment<WebViewPresenter>(), WebViewContract.View, BackFragment {

    override val scrollViewId = R.id.web_view

    override val presenter = WebViewPresenter()

    override fun getWebViewDisplayable(): WebViewDisplayable? {
        return _webViewDisplayable
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated()
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.visibility =
            View.VISIBLE
    }

    override fun onPause() {
        activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.visibility = View.GONE
        super.onPause()
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
            activity?.findViewById<WebView>(R.id.web_view)?.loadUrl(url)
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
