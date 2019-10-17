package de.taz.app.android

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.drawer.bookmarks.BookmarkDrawerFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.drawer.sectionList.SelectedIssueViewModel
import de.taz.app.android.ui.login.LoginFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.nav_header_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private val log by Log
    private val apiService = ApiService.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toastHelper = ToastHelper.getInstance(applicationContext)

        setContentView(R.layout.activity_main)
        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        downloadLatestIssue()

        setNavigationDrawerItemActions()
        findViewById<View>(R.id.drawer_icon_content).performClick()

        val viewModel = ViewModelProviders.of(this@MainActivity).get(SelectedIssueViewModel::class.java)

        viewModel.selectedIssueDownloaded.observe(this@MainActivity, Observer {
            isDownloaded ->
                if(isDownloaded)
                    toastHelper.makeToast("issue downloaded")
        })
    }

    private fun setNavigationDrawerItemActions() {
        drawer_icon_content.setOnClickListener {
            highlightIcon(drawer_icon_content)
            setDrawerTitle(R.string.navigation_drawer_icon_content)
            showDrawerFragment(SectionDrawerFragment())
        }

        drawer_icon_home.setOnClickListener {
            highlightIcon(drawer_icon_home)
            setDrawerTitle(R.string.navigation_drawer_icon_home)
            // TODO
            ToastHelper.getInstance().makeToast("should show home")
        }

        drawer_icon_bookmarks.setOnClickListener {
            highlightIcon(drawer_icon_bookmarks)
            setDrawerTitle(R.string.navigation_drawer_icon_bookmarks)
            showDrawerFragment(BookmarkDrawerFragment())
        }

        drawer_icon_settings.setOnClickListener {
            // highlightIcon(drawer_icon_settings)
            // setDrawerTitle(R.string.navigation_drawer_icon_settings)
            showMainFragment(LoginFragment())
            closeDrawer()
        }

        drawer_icon_help.setOnClickListener {
            highlightIcon(drawer_icon_help)
            setDrawerTitle(R.string.navigation_drawer_icon_help)
            // TODO
            ToastHelper.getInstance().makeToast("should show help")
        }
    }

    private fun highlightIcon(imageView: ImageView) {
        listOf(
            drawer_icon_content,
            drawer_icon_home,
            drawer_icon_bookmarks,
            drawer_icon_settings,
            drawer_icon_help
        ).map { it.drawable.setTint(ContextCompat.getColor(this, R.color.navigation_drawer_icon)) }

        imageView.drawable.setTint(
            ContextCompat.getColor(
                this,
                R.color.navigation_drawer_icon_selected
            )
        )
    }

    private fun setDrawerTitle(@StringRes stringId: Int) {
        drawer_header_title.text = getString(stringId).toLowerCase(Locale.getDefault())
    }

    private fun showDrawerFragment(fragment: Fragment) {
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


    fun showArticle(article: Article, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0) {
        runOnUiThread {
            showMainFragment(ArticleWebViewFragment(article), enterAnimation, exitAnimation)
        }
    }

    fun showSection(section: Section, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0) {
        runOnUiThread {
            showMainFragment(SectionWebViewFragment(section), enterAnimation, exitAnimation)
        }
    }

    private fun showMainFragment(fragment: Fragment, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0) {
        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(enterAnimation, exitAnimation)
                .replace(
                    R.id.main_content_fragment_placeholder, fragment
                ).commit()
        }
    }

    fun closeDrawer() {
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

    private fun downloadLatestIssue() {
        val issueRepository = IssueRepository.getInstance(applicationContext)
        val toastHelper = ToastHelper.getInstance(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val issue = apiService.getIssueByFeedAndDate()
                issueRepository.save(issue)
                DownloadService.download(applicationContext, issue)
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                toastHelper.showNoConnectionToast()
            } catch (e: ApiService.ApiServiceException.InsufficientDataException) {
                toastHelper.makeToast(R.string.something_went_wrong_try_later)
            } catch (e: ApiService.ApiServiceException.WrongDataException) {
                toastHelper.makeToast(R.string.something_went_wrong_try_later)
            }
        }
    }

}
