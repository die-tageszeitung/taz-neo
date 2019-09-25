package de.taz.app.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.download.DownloadService
import de.taz.app.android.fragments.WebViewFragment
import androidx.lifecycle.Observer
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_webview.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = AuthHelper.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        setContentView(R.layout.activity_main)

        val apiService = ApiService()

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
                                runOnUiThread {
                                    showIssue(issue)
                                }
                            }
                        }
                    })
            })

        login.setOnClickListener {
            GlobalScope.launch {
                try {
                    apiService.authenticate(
                        username.text.toString(), password.text.toString()
                    ).token?.let {
                        authHelper.token = it
                    }
                } catch (e: Exception) {
                    toastHelper.makeToast(R.string.toast_login_failed)
                }
            }
        }

    }

    private fun showIssue(issue: Issue) {
        val file = File(
            ContextCompat.getExternalFilesDirs(applicationContext, null).first(),
            "${issue.tag}/${issue.sectionList.first().sectionHtml.name}"
        )
        runOnUiThread { helloWorld.loadUrl("file://${file.absolutePath}") }
    }

}
