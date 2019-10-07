package de.taz.app.android

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.Section
import de.taz.app.android.download.DownloadService
import de.taz.app.android.ui.webview.WebViewFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.drawer.sectionList.SectionListFragment
import de.taz.app.android.ui.drawer.sectionList.SelectedIssueViewModel
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val log by Log

    private val apiService: ApiService = ApiService()
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper

    private lateinit var viewModel: SelectedIssueViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
        issueRepository = IssueRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        viewModel = ViewModelProviders.of(this@MainActivity).get(SelectedIssueViewModel::class.java)
        supportFragmentManager.beginTransaction().replace(
            R.id.drawerMenuFragmentPlaceHolder,
            SectionListFragment()
        ).commit()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val issue = apiService.getIssueByFeedAndDate()
                issueRepository.save(issue)
                DownloadService.download(applicationContext, issue)
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                toastHelper.showNoConnectionToast()
            } catch (e: ApiService.ApiServiceException.InsufficientData) {
                toastHelper.makeToast(R.string.toast_unknown_error)
            }
        }

        issueRepository.getLatestIssueBaseLiveData()
            .observe(this@MainActivity, Observer { issueBase ->
                log.debug("issue exists in db")
                issueBase?.isDownloadedLiveData()
                    ?.observe(this@MainActivity, Observer { downloaded ->
                        if (downloaded) {
                            log.debug("issue is downloaded")
                            CoroutineScope(Dispatchers.IO).launch {
                                val issue = issueBase.getIssue()
                                viewModel.selectedIssue.postValue(issue)
                                showIssue(issue)
                            }
                        }
                    })
            })
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
     * Workaround for AppCompat 1.1.0 and Webview on API 21 - 25
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
