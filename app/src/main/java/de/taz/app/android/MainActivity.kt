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
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.fragment_webview.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private val authHelper = AuthHelper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiService = ApiService()

        CoroutineScope(Dispatchers.IO).launch {
            val lastIssue =  apiService.getIssueByFeedAndDate()
            IssueRepository().save(lastIssue)
            DownloadService.download(applicationContext, lastIssue)
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
                IssueRepository().getLatestIssue()?.let { lastIssue ->

                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragmentPlaceholder, WebViewFragment(lastIssue))
                        .commit()
                }
            }
        }

    }

}