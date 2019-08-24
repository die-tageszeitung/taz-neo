package de.taz.app.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var apiService: ApiService

    override fun onResume() {
        super.onResume()
        createSingletons()
        GlobalScope.launch {
            DownloadService.downloadResources(applicationContext, apiService.getResourceInfo())
            DownloadService.downloadIssue(applicationContext, apiService.getIssueByFeedAndDate())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun createSingletons() {
        AuthHelper.createInstance(applicationContext)
        QueryService.createInstance(applicationContext)
        ToastHelper.createInstance(applicationContext)
        apiService = ApiService()
    }

}

