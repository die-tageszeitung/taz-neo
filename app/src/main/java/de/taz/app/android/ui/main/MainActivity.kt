package de.taz.app.android.ui.main

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageView
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.archive.main.ArchiveFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.nav_header_main.*
import java.util.*

class MainActivity : AppCompatActivity(), MainContract.View {

    private val toastHelper = ToastHelper.getInstance()

    private val presenter = MainPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        presenter.attach(this)

        setContentView(R.layout.activity_main)
        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        setNavigationDrawerIconClickListener()
        drawer_icon_content.performClick()

        presenter.onViewCreated()

        lockEndNavigationView()
    }

    private fun setNavigationDrawerIconClickListener() {
        listOf(
            drawer_icon_content,
            drawer_icon_home,
            drawer_icon_bookmarks,
            drawer_icon_settings,
            drawer_icon_help
        ).map { imageView ->
            imageView.setOnClickListener {
                presenter.onItemClicked(imageView)
            }
        }
    }

    override fun getMainDataController(): MainContract.DataController {
        return presenter.viewModel as MainContract.DataController
    }

    override fun highlightDrawerIcon(imageView: ImageView) {
        listOf(
            drawer_icon_content,
            drawer_icon_home,
            drawer_icon_bookmarks,
            drawer_icon_settings,
            drawer_icon_help
        ).map {
            it.drawable.setTint(ContextCompat.getColor(this, R.color.navigation_drawer_icon))
        }

        imageView.drawable.setTint(
            ContextCompat.getColor(
                this,
                R.color.navigation_drawer_icon_selected
            )
        )
    }

    override fun setDrawerTitle(@StringRes stringId: Int) {
        drawer_header_title.text = getString(stringId).toLowerCase(Locale.getDefault())
    }

    override fun showDrawerFragment(fragment: Fragment) {
        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.drawer_menu_fragment_placeholder,
                    fragment
                )
                .commit()
        }
    }


    override fun showInWebView(
        webViewDisplayable: WebViewDisplayable,
        enterAnimation: Int,
        exitAnimation: Int
    ) {
        runOnUiThread {
            val fragment = when (webViewDisplayable) {
                is Article ->
                    ArticleWebViewFragment(webViewDisplayable)
                is Section ->
                    SectionWebViewFragment(webViewDisplayable)
                else -> null
            }
            fragment?.let {
                showMainFragment(fragment, enterAnimation, exitAnimation)
            }
        }
    }

    override fun showMainFragment(fragment: Fragment, @AnimRes enterAnimation: Int, @AnimRes exitAnimation: Int) {
        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(enterAnimation, exitAnimation)
                .replace(
                    R.id.main_content_fragment_placeholder, fragment
                ).commit()
        }
    }

    override fun lockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    override fun unlockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
    }

    override fun closeDrawer() {
        drawer_layout.closeDrawers()
    }

    /**
     * Workaround for AppCompat 1.1.0 and WebView on API 21 - 25
     * See: https://issuetracker.google.com/issues/141132133
     * TODO: try to remove when updating appcompat
     */
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (Build.VERSION.SDK_INT in 21..25 && (resources.configuration.uiMode == applicationContext.resources.configuration.uiMode)) {
            return
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    /**
     * if currently shown fragment implements onBackPressed and returns true it has handled the
     * back button
     */
    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.main_content_fragment_placeholder)?.let {
            if (it is BackFragment && it.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun showArchive() {
        showMainFragment(ArchiveFragment())
    }

    override fun showToast(stringId: Int) {
        toastHelper.makeToast(stringId)
    }

    override fun showToast(string: String) {
        toastHelper.makeToast(string)
    }

    override fun getLifecycleOwner(): LifecycleOwner = this

    override fun getMainView(): MainContract.View? = this

}
