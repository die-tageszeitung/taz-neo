package de.taz.app.android.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
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
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.BuildConfig
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.WelcomeActivity
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
            showMainFragment(fragment)
        }
    }

    override fun showInWebView(
        webViewDisplayable: WebViewDisplayable,
        enterAnimation: Int,
        exitAnimation: Int,
        bookmarksArticle: Boolean
    ) {
        when (webViewDisplayable) {
            is Article -> showArticle(
                webViewDisplayable,
                enterAnimation,
                exitAnimation,
                bookmarksArticle
            )
            is Section -> showSection(webViewDisplayable, enterAnimation, exitAnimation)
        }
    }

    private fun showArticle(
        article: Article,
        enterAnimation: Int = 0,
        exitAnimation: Int = 0,
        bookmarksArticle: Boolean = false
    ) {
        val fragment = ArticlePagerFragment.createInstance(article, bookmarksArticle)
        showMainFragment(fragment, enterAnimation, exitAnimation)
    }

    private fun showSection(section: Section, enterAnimation: Int, exitAnimation: Int) {
        runOnUiThread {
            if (!tryShowExistingSection(section)) {
                val fragment = SectionPagerFragment.createInstance(section)
                showMainFragment(fragment, enterAnimation, exitAnimation)
            }
        }
    }

    override fun showIssue(issueStub: IssueStub) {
        presenter.showIssue(issueStub)
    }

    @MainThread
    private fun tryShowExistingSection(section: Section): Boolean {
        supportFragmentManager.popBackStackImmediate(SectionPagerFragment::class.java.name, 0)
        val sectionPagerFragment =
            supportFragmentManager.findFragmentById(R.id.main_content_fragment_placeholder)
        if (sectionPagerFragment is SectionPagerFragment) {
            return sectionPagerFragment.tryLoadSection(section)
        }
        return false
    }

    override fun showMainFragment(
        fragment: Fragment,
        @AnimRes enterAnimation: Int,
        @AnimRes exitAnimation: Int
    ) {
        runOnUiThread {
            val fragmentClassName = fragment::class.java.name

            if (!supportFragmentManager.popBackStackImmediate(fragmentClassName, 0)) {
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

        if (count > 0) {
            supportFragmentManager
                .findFragmentById(R.id.main_content_fragment_placeholder)?.let {
                    if (it is BackFragment && it.onBackPressed()) {
                        return
                    } else {
                        supportFragmentManager.popBackStack()
                    }
                }
        } else {
            super.onBackPressed()
        }
    }

    override fun showHome() {
        showMainFragment(HomeFragment())
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
                                ArticleRepository.getInstance(
                                    applicationContext
                                ).get(articleName)?.let { article ->
                                    showArticle(article)
                                }
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
