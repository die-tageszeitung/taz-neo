package de.taz.app.android

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.QueryService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.download.RESOURCE_FOLDER
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.*
import kotlin.Exception

class SplashActivity : AppCompatActivity() {

    private val log by Log

    override fun onResume() {
        super.onResume()
        createSingletons()

        initAppInfo()
        initResources()

        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun createSingletons() {
        applicationContext.let {
            AppDatabase.createInstance(it)

            AppInfoRepository.createInstance(it)
            ArticleRepository.createInstance(it)
            DownloadRepository.createInstance(it)
            FileEntryRepository.createInstance(it)
            IssueRepository.createInstance(it)
            PageRepository.createInstance(it)
            ResourceInfoRepository.createInstance(it)
            SectionRepository.createInstance(it)

            AuthHelper.createInstance(it)
            QueryService.createInstance(it)
            ToastHelper.createInstance(it)

            ApiService.createInstance(it)
            FileHelper.createInstance(it)
        }
    }

    /**
     * download AppInfo and persist it
     */
    private fun initAppInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppInfoRepository.getInstance(applicationContext).save(
                    ApiService.getInstance(applicationContext).getAppInfo()
                )
            } catch (e: Exception) {
                log.warn("unable to get AppInfo", e)
            }
        }
    }

    /**
     * download resources, save to db and download necessary files
     */
    private fun initResources() {
        val apiService = ApiService.getInstance(applicationContext)
        val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        val fileHelper = FileHelper.getInstance(applicationContext)
        val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fromServer = apiService.getResourceInfo()
                val local = resourceInfoRepository.get()

                if (local == null || fromServer.resourceVersion > local.resourceVersion || !local.isDownloadedOrDownloading()) {
                    resourceInfoRepository.save(fromServer)

                    // delete old stuff
                    local?.let { resourceInfoRepository.delete(local) }
                    fromServer.resourceList.forEach { newFileEntry ->
                        fileEntryRepository.get(newFileEntry.name)?.let { oldFileEntry ->
                            // only delete modified files
                            if (oldFileEntry != newFileEntry) {
                                oldFileEntry.delete()
                            }
                        }
                    }

                    // ensure resources are downloaded
                    DownloadService.scheduleDownload(applicationContext, fromServer)
                    DownloadService.download(applicationContext, fromServer)
                }
            } catch (e: Exception) {
                log.warn("unable to get ResourceInfo", e)
            }
        }

        // mock tazApi.css
        // TODO use real tazApi.css
        fileHelper.getFile(RESOURCE_FOLDER).mkdirs()
        fileHelper.getFile("$RESOURCE_FOLDER/tazApi.css").createNewFile()
        fileHelper.getFile("$RESOURCE_FOLDER/tazApi.js")
            .writeText(fileHelper.readFileFromAssets("js/tazApi.js"))
    }

}

