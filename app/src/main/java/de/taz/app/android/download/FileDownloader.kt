package de.taz.app.android.download

import android.content.Context
import de.taz.app.android.COPY_BUFFER_SIZE
import de.taz.app.android.FILE_DOWNLOAD_RETRY_INDEFINITELY
import de.taz.app.android.MAX_SIMULTANEOUS_DOWNLOADS
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.api.models.AppInfoKey
import de.taz.app.android.api.transformToConnectivityException
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.ContentDownload
import de.taz.app.android.content.cache.FileCacheItem
import de.taz.app.android.content.cache.FileEntryOperation
import de.taz.app.android.data.HTTP_CLIENT_ENGINE
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.sentry.SentryWrapperLevel
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.Executors

/**
 * [FileDownloader] is used by [ContentDownload] to download individual files
 */
class FileDownloader(
    private val applicationContext: Context
) : FiledownloaderInterface {
    companion object : SingletonHolder<FiledownloaderInterface, Context>(::FileDownloader)

    private val downloaderThreadPool = Executors.newFixedThreadPool(MAX_SIMULTANEOUS_DOWNLOADS)
    private val log by Log

    // reverse order from highest (priority) to lowest instead natural order (low to high)
    private val queue = CacheItemQueue
    private lateinit var downloadConnectionHelper: DownloadConnectionHelper


    private var downloaderJob: Job? = null

    override suspend fun enqueueDownload(operation: ContentDownload) {
        operation.notifyStart()
        for (download in operation.cacheItems) {
            log.debug("Offering ${download.item.fileEntryOperation.fileEntry.name} with priority ${download.item.priority()}")
            queue.sendOrNotify(
                download
            )
        }
        ensureDownloaderRunning()
    }

    private val httpClient: HttpClient = HttpClient(HTTP_CLIENT_ENGINE)

    private suspend fun ensureHelperInitialized() {
        val contentService = ContentService.getInstance(applicationContext)
        if (!::downloadConnectionHelper.isInitialized) {
            val healthCheckUrl = (contentService.downloadMetadata(
                AppInfoKey()
            ) as AppInfo).globalBaseUrl
            downloadConnectionHelper = DownloadConnectionHelper(healthCheckUrl)
        }
    }

    private fun ensureDownloaderRunning() {
        if (downloaderJob == null || downloaderJob?.isActive == false) {
            downloaderJob = CoroutineScope(Dispatchers.Default).launch {
                (0 until MAX_SIMULTANEOUS_DOWNLOADS).map { i ->
                    launch(downloaderThreadPool.asCoroutineDispatcher()) { pollForDownload(i) }
                }.joinAll()
            }
        }
    }

    private suspend fun pollForDownload(downloadAgentId: Int) {
        while (true) {
            val (nextDownload, operations) = queue.receive()
            log.debug("Agent $downloadAgentId Picked ${nextDownload.fileEntryOperation.fileEntry.name} with priority ${nextDownload.priority()}")
            downloadCacheItem(nextDownload, operations)
        }
    }

    private suspend fun downloadCacheItem(download: FileCacheItem, operations: List<ContentDownload>) {
        try {
            ensureHelperInitialized()
            val response = downloadConnectionHelper.retryOnConnectivityFailure({
                operations.map { it.notifyBadConnection() }
            }, maxRetries = FILE_DOWNLOAD_RETRY_INDEFINITELY) {
                transformToConnectivityException {
                    httpClient.get(
                        download.fileEntryOperation.origin!!
                    )
                }
            }
            val fileName = download.fileEntryOperation.fileEntry.name

            when (response.status.value) {
                in 200..299 -> {
                    val channel = response.body<ByteReadChannel>()
                    val hash = saveFile(download.fileEntryOperation, channel)
                    if (hash != download.fileEntryOperation.fileEntry.sha256) {
                        val hint = "Hash mismatch on ${download.fileEntryOperation.fileEntry.name}.\n" +
                                "Local hash $hash vs remote hash ${download.fileEntryOperation.fileEntry.sha256}"
                        log.warn(hint
                        )
                        SentryWrapper.captureMessage(hint, SentryWrapperLevel.WARNING)
                    }
                    download.fileEntryOperation.fileEntry.setDownloadDate(Date(), applicationContext)
                    operations.map {
                        it.notifySuccessfulItem()
                    }
                    log.verbose("Download of $fileName successful")
                }
                in 400..499 -> {
                    val hint =
                        "Response code ${response.status.value} while trying to download $fileName"
                    log.warn("Download of $fileName not successful ${response.status.value}")
                    val exception = ConnectivityException.ImplementationException(
                        hint,
                        null,
                        response
                    )
                    operations.map { it.notifyFailedItem(exception) }
                    SentryWrapper.captureException(exception)
                }
                in 500..599 -> {
                    val hint =
                        "Response code ${response.status.value} while trying to download $fileName"
                    log.warn(hint)
                    val exception = ConnectivityException.ServerUnavailableException(hint)
                    operations.map { it.notifyFailedItem(exception) }
                    SentryWrapper.captureException(exception)
                }
                else -> {
                    val hint = "Unexpected code ${response.status.value} for  $fileName"
                    log.warn(hint)
                    operations.map { it.notifyFailedItem(ConnectivityException.ImplementationException(hint)) }
                }
            }

        } catch (e: Exception) {
            operations.map { it.notifyFailedItem(e) }
            SentryWrapper.captureException(e)
        } finally {
            operations.map { it.checkIfItemsCompleteAndNotifyResult(Unit) }
        }
    }

    private suspend fun saveFile(download: FileEntryOperation, channel: ByteReadChannel): String {
        val file = File(download.destination!!)
        file.parentFile?.mkdirs()
        // clear out file
        file.writeBytes(ByteArray(0))
        val fileStream = file.outputStream()
        val hash = MessageDigest.getInstance("SHA-256")

        fileStream.use {
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            do {
                val read = channel.readAvailable(buffer)
                if (read > 0) {
                    hash.update(buffer, 0, read)
                    it.write(buffer, 0, read)
                }
            } while (read > 0)
        }

        val digest = hash.digest()
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}