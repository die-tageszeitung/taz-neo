package de.taz.app.android.ui.webview

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_section.web_view
import kotlinx.android.synthetic.main.include_loading_screen.*


abstract class WebViewFragment<DISPLAYABLE : WebViewDisplayable> :
    BaseMainFragment<WebViewPresenter<DISPLAYABLE>>(),
    WebViewContract.View<DISPLAYABLE> {

    private val log by Log

    private lateinit var tazApiCssPreferences : SharedPreferences

    private val tazApiCssPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        log.debug("WebViewFragment: shared pref changed: $key")
        presenter.injectCss(sharedPreferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

       getMainView()?.getApplicationContext()?.let { applicationContext ->
           tazApiCssPreferences = applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)
           tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
       }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

    }

    override fun getWebView(): AppWebView? {
        return web_view
    }

    override fun hideLoadingScreen() {
        activity?.runOnUiThread {
            loading_screen?.visibility = View.GONE
            web_view?.visibility = View.VISIBLE
        }
    }

    override fun loadUrl(url: String) {
        activity?.runOnUiThread {
            view?.findViewById<AppWebView>(R.id.web_view)?.loadUrl(url)
        }
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

    override fun showBookmarkBottomSheet() {
        val article = getWebViewDisplayable() as? Article
        article?. let {
            showBottomSheet(BookmarkSheetFragment(article.articleFileName))
        }
    }

    override fun showFontSettingBottomSheet() {
        showBottomSheet(TextSettingsFragment())
    }

    override fun onDestroy() {
        super.onDestroy()
        web_view?.destroy()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }
}
