package de.taz.app.android.ui.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.BuildConfig
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.SETTINGS_TEXT_NIGHT_MODE
import de.taz.app.android.singletons.TazApiCssHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import io.sentry.Sentry
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

@Mockable
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val log by Log

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

        // test to ensure sentry is working as expected
        // TODO remove in next release
        Sentry.capture("BOOOOM - DEBUG: ${BuildConfig.DEBUG}")

        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

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
                    changeDrawerIssue()
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

    fun showInWebView(
        webViewDisplayableKey: String,
        bookmarksArticle: Boolean = false
    ) {
        if (webViewDisplayableKey.startsWith("art")) {
            showArticle(
                webViewDisplayableKey,
                bookmarksArticle
            )
        } else {
            showSection(webViewDisplayableKey)
        }
    }

    private fun showArticle(
        articleName: String,
        bookmarksArticle: Boolean = false
    ) {
        runOnUiThread {
            if (bookmarksArticle || !tryShowExistingArticle(articleName)) {
                val fragment = ArticlePagerFragment.createInstance(
                    articleName, showBookmarks = bookmarksArticle
                )
                showMainFragment(fragment, false)
            }
        }
    }

    private fun showSection(sectionFileName: String) {
        runOnUiThread {
            if (!tryShowExistingSection(sectionFileName)) {
                val fragment = SectionPagerFragment.createInstance(sectionFileName)
                showMainFragment(fragment, false)
            }
        }
    }

    fun showIssue(issueStub: IssueStub) {
        runOnUiThread {
            val fragment = SectionPagerFragment.createInstance(issueStub)
            showMainFragment(fragment, showFromBackStack = false)
        }
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

    fun showMainFragment(
        fragment: Fragment,
        showFromBackStack: Boolean = true
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

    fun lockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    fun unlockEndNavigationView() {
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
    }

    fun isDrawerVisible(gravity: Int): Boolean {
        return drawer_layout.isDrawerVisible(gravity)
    }

    fun openDrawer() {
        openDrawer(GravityCompat.START)
    }

    fun openDrawer(gravity: Int) {
        drawer_layout.openDrawer(gravity)
    }

    fun closeDrawer() {
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
            finish()
        }
    }

    fun showHome() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    fun showToast(stringId: Int) {
        ToastHelper.getInstance(applicationContext).showToast(stringId)
    }

    fun showToast(string: String) {
        ToastHelper.getInstance(applicationContext).showToast(string)
    }

    fun getLifecycleOwner(): LifecycleOwner = this

    fun getMainView(): MainActivity? = this

    fun setDrawerIssue(issueOperations: IssueOperations) {
        (supportFragmentManager.findFragmentById(
            R.id.drawer_menu_fragment_placeholder
        ) as? SectionDrawerFragment)?.apply {
            setIssueOperations(issueOperations)
        }
    }

    fun changeDrawerIssue() {
        runOnUiThread {
            (supportFragmentManager.findFragmentById(
                R.id.drawer_menu_fragment_placeholder
            ) as? SectionDrawerFragment)?.apply {
                showIssueStub()
            }
        }
    }

    private var navButton: Image? = null
    private var defaultNavButton: Image? = null
    fun setDrawerNavButton(navButton: Image? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            suspendSetDrawerNavButton(navButton)
        }
    }

    private suspend fun suspendSetDrawerNavButton(navButton: Image?) {
        withContext(Dispatchers.IO) {
            if (defaultNavButton == null) {
                //  get defaultNavButton
                defaultNavButton = ImageRepository.getInstance().get(DEFAULT_NAV_DRAWER_FILE_NAME)
            }

            val image: Image? = navButton ?: defaultNavButton
            image?.let {
                // if image exists wait for it to be downloaded and show it
                val isDownloadedLiveData = image.isDownloadedLiveData()
                withContext(Dispatchers.Main) {
                    isDownloadedLiveData.observeDistinct(getLifecycleOwner()) { isDownloaded ->
                        if (isDownloaded) {
                            showNavButton(image)
                        }
                    }
                }
            }
        }
    }

    private fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            runOnUiThread {
                // the scalingFactor is used to scale the image as using 100dp instead of 100px
                // would be too big - the value is taken from experience rather than science
                val scalingFactor = 1f / 3f

                val file = FileHelper.getInstance().getFile(navButton)
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        bitmap.width.toFloat(),
                        resources.displayMetrics
                    ) * scalingFactor).toInt(),
                    (TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        bitmap.height.toFloat(),
                        resources.displayMetrics
                    ) * scalingFactor).toInt(),
                    false
                )

                findViewById<ImageView>(R.id.drawer_logo)?.apply {
                    setImageBitmap(scaledBitmap)
                    imageAlpha = (navButton.alpha * 255).toInt()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ACTIVITY_LOGIN_REQUEST_CODE) {
            data?.let {
                data.getStringExtra(MAIN_EXTRA_TARGET)?.let {
                    if (it == MAIN_EXTRA_TARGET_ARTICLE) {
                        data.getStringExtra(MAIN_EXTRA_ARTICLE)?.let { articleName ->
                            showHome()
                            showArticle(articleName)
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
