package de.taz.app.android.ui.main

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseActivity
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.BookmarkPagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

@Mockable
class MainActivity : BaseActivity(R.layout.activity_main) {

    private val fileHelper = FileHelper.getInstance()
    private val imageRepository = ImageRepository.getInstance()
    private val sectionRepository = SectionRepository.getInstance()
    private val toastHelper = ToastHelper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    fun showInWebView(
        webViewDisplayableKey: String,
        bookmarksArticle: Boolean = false
    ) {
        if (webViewDisplayableKey.startsWith("art")) {
            if (bookmarksArticle) {
                showBookmark(webViewDisplayableKey)
            } else {
                showArticle(webViewDisplayableKey)
            }
        } else {
            showSection(webViewDisplayableKey)
        }
    }

    private fun showArticle(articleName: String) {
        runOnUiThread {
            val fragment = ArticlePagerFragment.createInstance(articleName)
            showMainFragment(fragment)
        }
    }

    private fun showBookmark(articleName: String) {
        runOnUiThread {
            val fragment = BookmarkPagerFragment.createInstance(articleName)
            showMainFragment(fragment)
        }
    }

    private fun showSection(sectionFileName: String) {
        runOnUiThread {
            if (!tryShowExistingSection(sectionFileName)) {
                val fragment = SectionPagerFragment.createInstance(sectionFileName)
                showMainFragment(fragment)
            }
        }
    }

    fun showIssue(issueStub: IssueStub) {
        setDrawerIssue(issueStub)
        setCoverFlowItem(issueStub)
        changeDrawerIssue()

        runOnUiThread {
            val fragment = SectionPagerFragment.createInstance(issueStub)
            showMainFragment(fragment)
        }
    }

    @MainThread
    private fun tryShowExistingSection(sectionFileName: String): Boolean {
        supportFragmentManager.apply {
            val currentTag = (backStackEntryCount - 1).toString()
            val currentFragment = findFragmentByTag(currentTag)
            if (currentFragment is SectionPagerFragment) {
                return currentFragment.tryLoadSection(sectionFileName)
            }
            val lastTag = (backStackEntryCount - 2).toString()
            val lastFragment = findFragmentByTag(lastTag)
            if (lastFragment is SectionPagerFragment) {
                popBackStackImmediate(lastTag, 0)
                return lastFragment.tryLoadSection(sectionFileName)
            }
            return false
        }
    }

    fun showMainFragment(fragment: Fragment) {
        runOnUiThread {
            supportFragmentManager.apply {
                beginTransaction()
                    .replace(
                        R.id.main_content_fragment_placeholder,
                        fragment,
                        backStackEntryCount.toString()
                    )
                    .addToBackStack(backStackEntryCount.toString())
                    .commit()
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
        toastHelper.showToast(stringId)
    }

    fun showToast(string: String) {
        toastHelper.showToast(string)
    }

    fun getLifecycleOwner(): LifecycleOwner = this

    fun getMainView(): MainActivity? = this

    fun setDrawerIssue(issueOperations: IssueOperations) {
        (supportFragmentManager.fragments.firstOrNull { it is SectionDrawerFragment } as? SectionDrawerFragment)?.apply {
            setIssueOperations(issueOperations)
        }
    }

    fun setActiveDrawerSection(activePosition: Int) {
        (supportFragmentManager.fragments.firstOrNull { it is SectionDrawerFragment } as? SectionDrawerFragment)?.apply {
            setActiveSection(activePosition)
        }
    }

    fun setActiveDrawerSection(sectionFileName: String) {
        (supportFragmentManager.fragments.firstOrNull { it is SectionDrawerFragment } as? SectionDrawerFragment)?.apply {
            setActiveSection(sectionFileName)
        }
    }

    fun setCoverFlowItem(issueStub: IssueStub) {
        val homeFragment =
            supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
        val coverFlowFragment =
            homeFragment?.childFragmentManager?.fragments?.firstOrNull { it is CoverflowFragment } as? CoverflowFragment
        runOnUiThread {
            coverFlowFragment?.skipToItem(issueStub)
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
                defaultNavButton = imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)
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

                val file = fileHelper.getFile(navButton)
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
                            CoroutineScope(Dispatchers.IO).launch {
                                sectionRepository.getSectionStubForArticle(articleName)
                                    ?.let { section ->
                                        section.getIssueOperations()?.let { issueOperations ->
                                            setCoverFlowItem(issueOperations)
                                            setDrawerIssue(issueOperations)
                                            changeDrawerIssue()
                                        }
                                    }
                            }
                            // clear fragment backstack before showing article
                            supportFragmentManager.popBackStackImmediate(
                                null,
                                FragmentManager.POP_BACK_STACK_INCLUSIVE
                            )
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
