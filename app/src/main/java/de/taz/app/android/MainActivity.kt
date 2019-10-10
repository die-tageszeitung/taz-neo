package de.taz.app.android

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.Section
import de.taz.app.android.download.DownloadService
import de.taz.app.android.ui.webview.WebViewFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.drawer.bookmarks.BookmarkDrawerFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.drawer.sectionList.SelectedIssueViewModel
import de.taz.app.android.util.FileHelper
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_drawer_menu_sections.*
import kotlinx.android.synthetic.main.nav_header_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val log by Log

    private val apiService = ApiService.getInstance()
    private val fileHelper = FileHelper.getInstance()
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper

    private lateinit var viewModel: SelectedIssueViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        issueRepository = IssueRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        viewModel = ViewModelProviders.of(this@MainActivity).get(SelectedIssueViewModel::class.java)
        supportFragmentManager.beginTransaction().replace(
            R.id.drawer_menu_fragment_placeholder,
            SectionDrawerFragment()
        ).commit()

        CoroutineScope(Dispatchers.IO).launch {
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


        lifecycleScope.launch {
            val issueLiveData = issueRepository.getLatestIssueBaseLiveData()
            issueLiveData.observe(this@MainActivity, Observer { issueBase ->
                lifecycleScope.launch {
                    val issue = issueBase?.getIssue()
                    issue?.let {
                        val isDownloadedLiveData = issue.isDownloadedLiveData()
                        isDownloadedLiveData.observe(
                            this@MainActivity,
                            Observer { downloaded ->
                                if (downloaded) {
                                    log.debug("issue is downloaded")
                                    viewModel.selectedIssue.postValue(issue)
                                    showIssue(issue)
                                }
                            })
                    }
                }
            })
        }

        drawer_icon_favorites.setOnClickListener {
            showDrawerFragment(BookmarkDrawerFragment())
        }

        drawer_icon_content.setOnClickListener {
            showDrawerFragment(SectionDrawerFragment())
        }

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


    private fun showIssue(issue: Issue) {

        showSection(issue.sectionList.first())
    }

    fun showSection(section: Section) {
        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_content_fragment_placeholder,
                    SectionWebViewFragment(section)
                )
                .commit()
            drawer_layout.closeDrawer(GravityCompat.START)
        }
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
}
