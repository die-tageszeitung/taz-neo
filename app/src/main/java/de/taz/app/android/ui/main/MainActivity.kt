package de.taz.app.android.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.annotation.AnimRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.BuildConfig
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

class MainActivity : AppCompatActivity(), MainContract.View {

    private val log by Log

    private val presenter = MainPresenter()

    private lateinit var tazApiCssPreferences: SharedPreferences

    private val tazApiCssPrefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            log.debug("Shared pref changed: $key")
            val cssFile = FileHelper.getInstance(applicationContext).getFileByPath(
                "$RESOURCE_FOLDER/tazApi.css"
            )
            val cssString = TazApiCssHelper.generateCssString(sharedPreferences)

            cssFile.writeText(cssString)

            if (key == SETTINGS_TEXT_NIGHT_MODE) {
                setThemeAndReCreate(sharedPreferences, true)
            }
        }

    private fun setThemeAndReCreate(
        sharedPreferences: SharedPreferences,
        isReCreateFlagSet: Boolean = false
    ) {
        if (sharedPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            log.debug("setTheme to NIGHT")
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            log.debug("setTheme to DAY")
        }
        if (isReCreateFlagSet) {
            recreate()
        }
    }

    private fun isDarkTheme(): Boolean {
        return this.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tazApiCssPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        // if "text_night_mode" is not set in shared preferences -> set it now
        if (!tazApiCssPreferences.contains(SETTINGS_TEXT_NIGHT_MODE)) {
            SharedPreferenceBooleanLiveData(
                tazApiCssPreferences, SETTINGS_TEXT_NIGHT_MODE, isDarkTheme()
            ).postValue(isDarkTheme())
        }

        if (tazApiCssPreferences.getBoolean(SETTINGS_TEXT_NIGHT_MODE, false) != isDarkTheme()) {
            setThemeAndReCreate(tazApiCssPreferences, false)
        }

        super.onCreate(savedInstanceState)

        presenter.attach(this)
        setContentView(R.layout.activity_main)
        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)

            activity_main_version.text = BuildConfig.VERSION_NAME
            activity_main_version.visibility = View.VISIBLE
        }

        presenter.onViewCreated(savedInstanceState)
        lockEndNavigationView()

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var opened = false

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit

            override fun onDrawerOpened(drawerView: View) {
                opened = true
            }

            override fun onDrawerClosed(drawerView: View) {
                opened = false
            }

            override fun onDrawerStateChanged(newState: Int) {
                if (!opened) {
                    presenter.setDrawerIssue()
                }
            }
        })

        if (savedInstanceState == null) {
            showHome()
        }
    }

    override fun onResume() {
        super.onResume()
        tazApiCssPreferences.registerOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    override fun onPause() {
        super.onPause()
        tazApiCssPreferences.unregisterOnSharedPreferenceChangeListener(tazApiCssPrefListener)
    }

    override fun getMainDataController(): MainContract.DataController {
        return presenter.viewModel as MainContract.DataController
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

    override fun showInWebView(issueStub: IssueStub) {
        runOnUiThread {
            val fragment = SectionPagerFragment.createInstance(issueStub)
            showMainFragment(fragment, showFromBackStack = false)
        }
    }

    override fun showInWebView(
        webViewDisplayableKey: String,
        enterAnimation: Int,
        exitAnimation: Int,
        bookmarksArticle: Boolean
    ) {
        if (webViewDisplayableKey.startsWith("art")) {
            showArticle(
                webViewDisplayableKey,
                enterAnimation,
                exitAnimation,
                bookmarksArticle
            )
        } else {
            showSection(webViewDisplayableKey, enterAnimation, exitAnimation)
        }
    }

    private fun showArticle(
        articleName: String,
        enterAnimation: Int = 0,
        exitAnimation: Int = 0,
        bookmarksArticle: Boolean = false
    ) {
        runOnUiThread {
            if (bookmarksArticle || !tryShowExistingArticle(articleName)) {
                val fragment = ArticlePagerFragment.createInstance(
                    articleName, showBookmarks = bookmarksArticle
                )
                showMainFragment(fragment, enterAnimation, exitAnimation, false)
            }
        }
    }

    private fun showSection(sectionFileName: String, enterAnimation: Int, exitAnimation: Int) {
        runOnUiThread {
            if (!tryShowExistingSection(sectionFileName)) {
                val fragment = SectionPagerFragment.createInstance(sectionFileName)
                showMainFragment(fragment, enterAnimation, exitAnimation, false)
            }
        }
    }

    override fun showIssue(issueStub: IssueStub) {
        presenter.showIssue(issueStub)
    }

    @MainThread
    private fun tryShowExistingSection(sectionFileName: String): Boolean {
        supportFragmentManager.popBackStackImmediate(SectionPagerFragment::class.java.name, 0)
        val sectionPagerFragment =
            supportFragmentManager.findFragmentById(R.id.main_content_fragment_placeholder)
        if (sectionPagerFragment is SectionPagerFragment) {
            return sectionPagerFragment.tryLoadSection(sectionFileName)
        }
        return false
    }

    @MainThread
    private fun tryShowExistingArticle(articleFileName: String): Boolean {
        supportFragmentManager.popBackStackImmediate(ArticlePagerFragment::class.java.name, 0)
        val articlePagerFragment =
            supportFragmentManager.findFragmentById(R.id.main_content_fragment_placeholder)
        if (articlePagerFragment is ArticlePagerFragment) {
            return articlePagerFragment.tryLoadArticle(articleFileName)
        }
        return false
    }

    override fun showMainFragment(
        fragment: Fragment,
        @AnimRes enterAnimation: Int,
        @AnimRes exitAnimation: Int,
        showFromBackStack: Boolean
    ) {
        runOnUiThread {
            val fragmentClassName = fragment::class.java.name

            if (!showFromBackStack || !supportFragmentManager.popBackStackImmediate(
                    fragmentClassName,
                    0
                )
            ) {
                supportFragmentManager.beginTransaction().apply {
                    replace(R.id.main_content_fragment_placeholder, fragment)
                    addToBackStack(fragmentClassName)
                    commit()
                }
            }
        }
    }

    override fun lockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    override fun unlockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
    }

    override fun openDrawer() {
        openDrawer(GravityCompat.START)
    }

    override fun openDrawer(gravity: Int) {
        drawer_layout.openDrawer(gravity)
    }

    override fun closeDrawer() {
        drawer_layout.closeDrawers()
    }

    /**
     * if currently shown fragment implements onBackPressed and returns true it has handled the
     * back button
     */
    override fun onBackPressed() {
        val count = supportFragmentManager.backStackEntryCount

        if (drawer_layout.isDrawerOpen(GravityCompat.START)
            || drawer_layout.isDrawerOpen(GravityCompat.END)
        ) {
            closeDrawer()
            return
        }


        if (count > 1) {
            supportFragmentManager
                .findFragmentById(R.id.main_content_fragment_placeholder)?.let {
                    if (it is BackFragment && it.onBackPressed()) {
                        return
                    } else {
                        supportFragmentManager.popBackStack()
                    }
                }
        } else {
            finish()
        }
    }

    override fun showHome() {
        showMainFragment(HomeFragment(), showFromBackStack = true)
    }

    override fun showToast(stringId: Int) {
        ToastHelper.getInstance(applicationContext).showToast(stringId)
    }

    override fun showToast(string: String) {
        ToastHelper.getInstance(applicationContext).showToast(string)
    }

    override fun getLifecycleOwner(): LifecycleOwner = this

    override fun getMainView(): MainContract.View? = this

    override fun setDrawerIssue(issueOperations: IssueOperations?) {
        lifecycleScope.launch(Dispatchers.IO) {
            presenter.viewModel?.setIssueOperations(issueOperations)
        }
    }

    override fun hideKeyboard() {
        val inputMethodManager: InputMethodManager = getSystemService(
            Activity.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        val view = currentFocus ?: View(this)
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ACTIVITY_LOGIN_REQUEST_CODE) {
            data?.let {
                data.getStringExtra(MAIN_EXTRA_TARGET)?.let {
                    if (it == MAIN_EXTRA_TARGET_ARTICLE) {
                        data.getStringExtra(MAIN_EXTRA_ARTICLE)?.let { articleName ->
                            CoroutineScope(Dispatchers.IO).launch {
                                showArticle(articleName)
                            }
                        }
                    }
                    if (it == MAIN_EXTRA_TARGET_HOME) {
                        showHome()
                    }
                }
            }
        }
    }

}
