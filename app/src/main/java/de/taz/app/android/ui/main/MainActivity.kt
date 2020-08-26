package de.taz.app.android.ui.main

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedDialogFragment
import de.taz.app.android.ui.webview.pager.BookmarkPagerFragment
import de.taz.app.android.ui.webview.pager.IssueContentFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlin.math.min

const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

@Mockable
class MainActivity : NightModeActivity(R.layout.activity_main) {

    private var fileHelper: FileHelper? = null
    private var imageRepository: ImageRepository? = null
    private var sectionRepository: SectionRepository? = null
    private var toastHelper: ToastHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileHelper = FileHelper.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)
        sectionRepository = SectionRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        lockNavigationView(GravityCompat.END)

        checkIfSubscriptionElapsed()

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var opened = false

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        drawer_logo.translationX = min(
                            slideOffset * (parentView.width - drawerWidth),
                            -5f * resources.displayMetrics.density
                        )
                    }
                }
            }


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
        if (bookmarksArticle) {
            showBookmark(webViewDisplayableKey)
        } else {
            showDisplayable(webViewDisplayableKey)
        }
    }

    private fun showDisplayable(articleName: String) {
        runOnUiThread {
            if ((supportFragmentManager.fragments.lastOrNull() as? IssueContentFragment)?.show(
                    articleName
                ) != true
            ) {
                val fragment = IssueContentFragment.createInstance(articleName)
                showMainFragment(fragment)
            }
        }
    }

    private fun showBookmark(articleName: String) {
        runOnUiThread {
            val fragment = BookmarkPagerFragment.createInstance(articleName)
            showMainFragment(fragment)
        }
    }

    fun showIssue(issueStub: IssueStub) {
        setDrawerIssue(issueStub)
        changeDrawerIssue()

        runOnUiThread {
            val fragment = IssueContentFragment.createInstance(issueStub)
            showMainFragment(fragment)
            drawer_layout.openDrawer(GravityCompat.START)
        }
        lifecycleScope.launch {
            delay(3000)
            if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
                runOnUiThread {
                    drawer_layout.closeDrawer(GravityCompat.START)
                }
            }
        }
    }

    private fun checkIfSubscriptionElapsed() {
        val authStatus = AuthHelper.getInstance(applicationContext).authStatus
        if (authStatus == AuthStatus.elapsed) {
            showSubscriptionElapsedPopup()
        }
    }

    fun showMainFragment(fragment: Fragment) {
        lifecycleScope.launchWhenResumed {
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
    }

    fun lockNavigationView(gravity: Int) {
        drawer_layout?.apply {
            closeDrawer(gravity)
            setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, gravity)
        }
        if (gravity == GravityCompat.START) {
            drawer_logo.visibility = View.GONE
        }
    }

    fun unlockNavigationView(gravity: Int) {
        drawer_layout?.apply {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, gravity)
        }
        if (gravity == GravityCompat.START) {
            drawer_logo.visibility = View.VISIBLE
        }
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

    fun showHome(skipToCurrentIssue: Boolean = false) {
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val homeFragment =
            supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
        val coverFlowFragment =
            homeFragment?.childFragmentManager?.fragments?.firstOrNull { it is CoverflowFragment } as? CoverflowFragment
        runOnUiThread {
            this.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
            if (skipToCurrentIssue) coverFlowFragment?.skipToEnd()
        }

    }

    fun showToast(stringId: Int) {
        toastHelper?.showToast(stringId)
    }

    fun showToast(string: String) {
        toastHelper?.showToast(string)
    }

    fun showSubscriptionElapsedPopup() {
        val popUpFragment = SubscriptionElapsedDialogFragment()
        popUpFragment.show(
            supportFragmentManager,
            "showSubscriptionElapsed"
        )
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

    fun setCoverFlowItem(issueOperations: IssueOperations) {
        val homeFragment =
            supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
        val coverFlowFragment =
            homeFragment?.childFragmentManager?.fragments?.firstOrNull { it is CoverflowFragment } as? CoverflowFragment
        runOnUiThread {
            coverFlowFragment?.skipToItem(issueOperations)
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
    private var navButtonBitmap: Bitmap? = null
    private var navButtonAlpha = 255f

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
                defaultNavButton = imageRepository?.get(DEFAULT_NAV_DRAWER_FILE_NAME)
            }

            val image: Image? = navButton ?: defaultNavButton
            image?.let {
                if (image.downloadedStatus == DownloadStatus.done ||
                    image.downloadedStatus == DownloadStatus.takeOld)
                {
                    showNavButton(image)
                } else {
                    // if image exists wait for it to be downloaded and show it
                    image.download(applicationContext)
                    val isDownloadedLiveData = image.isDownloadedLiveData(applicationContext)
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
    }

    private fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            runOnUiThread {
                // the scalingFactor is used to scale the image as using 100dp instead of 100px
                // would be too big - the value is taken from experience rather than science
                val scalingFactor = 1f / 3f

                val file = fileHelper?.getFile(navButton)
                BitmapFactory.decodeFile(file?.absolutePath)?.let { bitmap ->
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
                    this.navButtonBitmap = scaledBitmap
                    this.navButtonAlpha = navButton.alpha
                    findViewById<ImageView>(R.id.drawer_logo)?.apply {
                        background = BitmapDrawable(resources, scaledBitmap)
                        alpha = navButton.alpha
                        imageAlpha = (navButton.alpha * 255).toInt()
                        drawer_layout.updateDrawerLogoBoundingBox(
                            scaledBitmap.width,
                            scaledBitmap.height
                        )
                    }
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
                            lifecycleScope.launch(Dispatchers.IO) {
                                sectionRepository?.getSectionStubForArticle(articleName)
                                    ?.let { section ->
                                        section.getIssueOperations(applicationContext)
                                            ?.let { issueOperations ->
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
                            recreate()
                            showDisplayable(articleName)
                        }
                    }
                    if (it == MAIN_EXTRA_TARGET_HOME) {
                        showHome(true)
                    }
                }
            }
        }
    }
}
