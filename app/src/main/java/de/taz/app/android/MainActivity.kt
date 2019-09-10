package de.taz.app.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity(private val apiService: ApiService = ApiService()) : AppCompatActivity() {

    private val authHelper = AuthHelper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        CoroutineScope(Dispatchers.IO).launch {
            IssueRepository().getLatestIssue()?.let { lastIssue ->
                val file = File(
                    ContextCompat.getExternalFilesDirs(applicationContext, null).first(),
                    "${lastIssue.tag}/${lastIssue.sectionList.first().sectionHtml.name}"
                )
                runOnUiThread { helloWorld.loadUrl("file://${file.absolutePath}") }
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
                IssueRepository().getLatestIssue()?.let { lastIssue ->
                    val file = File(
                        ContextCompat.getExternalFilesDirs(applicationContext, null).first(),
                        "${lastIssue.date}/${lastIssue.sectionList.first().sectionHtml.name}"
                    )
                    runOnUiThread { helloWorld.loadUrl("file://${file.absolutePath}") }
                }
            }
        }

    }

}