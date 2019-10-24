package de.taz.app.android.ui.webview

import android.content.Context
import android.os.Bundle
import android.view.*
import android.webkit.WebView
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import kotlinx.android.synthetic.main.fragment_webview.*


abstract class WebViewFragment(private val _webViewDisplayable: WebViewDisplayable? = null) :
    BottomNavigationFragment(), WebViewContract.View, BackFragment
{

    @get:LayoutRes
    abstract val headerId: Int

    private val presenter = WebViewPresenter()

    override fun getWebViewDisplayable(): WebViewDisplayable? {
        return _webViewDisplayable
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        configureHeader()
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

        setHeader()

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

    abstract fun configureHeader()

    private fun setHeader() {
        (header_placeholder as ViewGroup).apply {
            addView(layoutInflater.inflate(headerId, this, false))
        }
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

    override fun getLifecycleOwner(): LifecycleOwner {
        return this@WebViewFragment
    }

    override fun onBackPressed(): Boolean {
        return presenter.onBackPressed()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

}
