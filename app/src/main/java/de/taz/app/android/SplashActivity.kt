package de.taz.app.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SplashActivity : AppCompatActivity() {

    private lateinit var apiService: ApiService

    override fun onResume() {
        super.onResume()
        createSingletons()
        GlobalScope.launch {
            try {
                AppInfoRepository().save(ApiService().getAppInfo())
                ToastHelper.getInstance(applicationContext)
                    .makeToast(AppInfoRepository().get().globalBaseUrl)
                ResourceInfoRepository().save(ApiService().getResourceInfo())
                ToastHelper.getInstance()
                    .makeToast(ResourceInfoRepository().getOrThrow().resourceList.first().name)
                val issue = ApiService().getIssueByFeedAndDate()
                IssueRepository().save(issue)
                ToastHelper.getInstance().makeToast(IssueRepository().getLatestIssueBase().feedName)

                ToastHelper.getInstance()
                    .makeToast(IssueRepository().getLatestIssue().sectionList.first().articleList.first().title.toString())
            } catch (e: Exception) {
                ToastHelper.getInstance(applicationContext).makeToast("no interwebzzzz")
            }
            //DownloadService.scheduleDownload(applicationContext, apiService.getResourceInfo())
            //DownloadService.scheduleDownload(applicationContext, issue)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun createSingletons() {
        AppDatabase.createInstance(applicationContext)
        AuthHelper.createInstance(applicationContext)
        QueryService.createInstance(applicationContext)
        ToastHelper.createInstance(applicationContext)
        apiService = ApiService()
    }

}

