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
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.*
import kotlin.Exception

class SplashActivity : AppCompatActivity() {

    private val log by Log

    private lateinit var apiService: ApiService

    private lateinit var appInfoRepository: AppInfoRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var resourceInfoRepository: ResourceInfoRepository

    override fun onResume() {
        super.onResume()
        createSingletons()

        initAppInfo()
        initResources()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
        startActivity(Intent(this, MainActivity::class.java))
    }

    /**
     * initialize singletons
     */
    private fun createSingletons() {
        AppDatabase.createInstance(applicationContext)
        AuthHelper.createInstance(applicationContext)
        QueryService.createInstance(applicationContext)
        ToastHelper.createInstance(applicationContext)
        apiService = ApiService()
        appInfoRepository = AppInfoRepository.createInstance(applicationContext)
        downloadRepository = DownloadRepository.createInstance(applicationContext)
        fileEntryRepository = FileEntryRepository()
        resourceInfoRepository = ResourceInfoRepository()
    }

    /**
     * download AppInfo and persist it
     */
    private fun initAppInfo() {
        GlobalScope.launch {
            try {
                appInfoRepository.save(ApiService().getAppInfo())
            } catch (e: Exception) {
                log.warn("unable to get AppInfo", e)
            }
        }
    }

    /**
     * download resources, save to db and download necessary files
     */
    private fun initResources() {
        GlobalScope.launch {
            try {
                val fromServer = apiService.getResourceInfo()
                val local = resourceInfoRepository.get()

                if (local == null || fromServer.resourceVersion > local.resourceVersion) {
                    resourceInfoRepository.save(fromServer)

                    // delete old stuff
                    local?.let { resourceInfoRepository.delete(local) }
                    fromServer.resourceList.forEach { newFileEntry ->
                        fileEntryRepository.get(newFileEntry.name)?.let { oldFileEntry ->
                            // only delete modified files
                            if (oldFileEntry != newFileEntry) {
                                downloadRepository.delete(oldFileEntry.name)
                                fileEntryRepository.delete(oldFileEntry)
                                // TODO delete file form disk?!
                            }
                        }
                    }
                }

                // ensure resources are downloaded
                DownloadService.scheduleDownload(applicationContext, fromServer)
                DownloadService.download(applicationContext, fromServer)
            } catch (e: Exception) {
                log.warn("unable to get ResourceInfo", e)
            }
        }
    }

}

