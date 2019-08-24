package de.taz.app.android.download

import android.content.Context
import android.content.Intent
import android.os.*
import android.system.Os
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import de.taz.app.android.util.awaitCallback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

const val ACTION_DOWNLOAD = "action.download"
const val EXTRA_DOWNLOAD = "extra.download"
const val RECEIVER = "receiver"

const val RESOURCE_FOLDER = "resources"


class DownloadService(
    private val httpClient: OkHttpClient = getHttpClient()
): JobIntentService() {

    companion object{
        init {
            // TODO how to replace this with coroutines!?
            Looper.prepare()
        }

        private val log by Log

        private const val DOWNLOAD_JOB_ID = 1312

        private val downloadResultReceiver = DownloadResultReceiver()

        fun enqueueWork(context: Context, download: Download) {
            val intent = Intent(context, DownloadService::class.java)
            intent.action = ACTION_DOWNLOAD
            intent.putExtra(EXTRA_DOWNLOAD, download.serialize())
            //intent.putExtra(RECEIVER, downloadResultReceiver)
            log.debug("downloading ${download.url}")
            enqueueWork(context, DownloadService::class.java, DOWNLOAD_JOB_ID, intent)
        }

        fun downloadResources(context: Context, resourceInfo: ResourceInfo) {
            //TODO download only if newer
            resourceInfo.resourceList.forEach {
                enqueueWork(context, Download(resourceInfo.resourceBaseUrl, RESOURCE_FOLDER, it))
            }
        }

        fun downloadIssue(context: Context, issue: Issue, pdf: Boolean = false) {
            //TODO download only if not downloaded
            val files = if(pdf) issue.fileListPdf else issue.fileList
            files.forEach {
                enqueueWork(context, Download(issue.baseUrl, it, issue.date))
            }
            linkResources(context, issue)
        }

        fun linkResources(context: Context, issue: Issue) {
            val newFolder = getFile(context, "${issue.date}/$RESOURCE_FOLDER")
            val resourceFolderFile = getFile(context, RESOURCE_FOLDER)
            log.debug("trying to link ${newFolder.absolutePath} to ${resourceFolderFile.absolutePath}")
            if (!createSymLink(newFolder, resourceFolderFile)) {
                // TODO copy resources
                log.error("linking failed")
            }
        }

        @JvmStatic
        fun createSymLink(symLinkFile: File, originalFile: File): Boolean {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Os.symlink(originalFile.absolutePath, symLinkFile.absolutePath)
                    return true
                }
                val libcore = Class.forName("libcore.io.Libcore")
                val fOs = libcore.getDeclaredField("os")
                fOs.isAccessible = true
                val os = fOs.get(null)
                val method = os.javaClass.getMethod("symlink", String::class.java, String::class.java)
                method.invoke(os, originalFile, symLinkFile)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        fun getFile(context: Context, fileName: String, internal: Boolean = false): File {
            val file =
                if(internal)
                    File(context.filesDir, fileName)
                else {
                    val file = File(ContextCompat.getExternalFilesDirs(context, null).first(), fileName)
                    file
                }
            return file
        }

    }

    override fun onHandleWork(intent: Intent) {
        log.debug("handling intent with action ${intent.action}")

        when (intent.action) {
            ACTION_DOWNLOAD -> {
                intent.getStringExtra(EXTRA_DOWNLOAD)?.let { serializedDownload ->
                    log.debug("received download: $serializedDownload")
                    GlobalScope.launch {
                        val download = Download.deserialize(serializedDownload)
                        val response = awaitCallback(httpClient.newCall(
                            Request.Builder().url(download.url).get().build()
                        )::enqueue)

                        val file = getFile(this@DownloadService, download.path)
                        response.body?.bytes()?.let {
                            try {
                                // ensure folders are created
                                getFile(this@DownloadService, download.folder).mkdirs()
                                file.writeBytes(it)
                            } catch (e: Exception) {
                                e
                            }
                        }
                        // TODO verifyDownload(download) in second service!?

                        ToastHelper.getInstance().makeToast("Downloaded ${file.name}")

                        val bundle = Bundle()
                        bundle.putString(BUNDLE_DOWNLOAD, download.serialize())
                        downloadResultReceiver.send(RESULT_OK, bundle)
                    }
                }
            }
        }
    }

    /* Checks if external storage is available for read and write */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /* Checks if external storage is available to at least read */
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

}


private fun getHttpClient(): OkHttpClient {
    return OkHttpClient.Builder().
        build()
}