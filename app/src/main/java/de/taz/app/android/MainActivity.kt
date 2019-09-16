package de.taz.app.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity(private val apiService: ApiService = ApiService()) : AppCompatActivity() {

    private lateinit var authHelper : AuthHelper
    private lateinit var issueRepository : IssueRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = AuthHelper.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)

        setContentView(R.layout.activity_main)

        CoroutineScope(Dispatchers.IO).launch {
            issueRepository.getLatestIssue()?.let { lastIssue ->
                lastIssue.sectionList.first().let { section ->
                    if (section.isDownloaded()) {
                        val file = File(
                            ContextCompat.getExternalFilesDirs(applicationContext, null).first(),
                            "${lastIssue.tag}/${section.sectionHtml.name}"
                        )
                        runOnUiThread { helloWorld.loadUrl("file://${file.absolutePath}") }
                    }
                }
            }
        }
        login.setOnClickListener {
            GlobalScope.launch {
                try {
                    apiService.authenticate(username.text.toString(), password.text.toString())
                        .token?.let {
                        authHelper.token = it
                    }
                } catch (e: Exception) {
                    // TODO
                }
            }
        }
        test.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    issueRepository.getLatestIssue()?.let {
                        if (it.isDownloaded())
                            showIssue(it)
                        else {
                            ToastHelper.getInstance().makeToast("PLZ WAIT")
                            DownloadService.download(applicationContext, it)
                        }
                    } ?: let {
                        val issue = apiService.getIssueByFeedAndDate()
                        issueRepository.save(issue)
                        DownloadService.download(applicationContext, issue)
                    }
                } catch (nie: ApiService.ApiServiceException.NoInternetException) {
                    ToastHelper.getInstance().showNoConnectionToast()
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