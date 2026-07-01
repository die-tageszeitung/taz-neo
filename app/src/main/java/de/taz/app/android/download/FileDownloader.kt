package de.taz.app.android.download

import android.content.Context
import de.taz.app.android.COPY_BUFFER_SIZE
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
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.io.BufferedOutputStream
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
    private val initializationMutex = Mutex()

    override suspend fun enqueueDownload(operation: ContentDownload) {
        operation.notifyStart()
        for (item in operation.cacheItems) {
            log.debug("Offering ${item.fileEntryOperation.fileEntry.name} with priority ${item.priority()}")
            queue.sendOrNotify(
                item, operation
            )
        }
        ensureDownloaderRunning()
    }

    private val httpClient: HttpClient = HttpClient(HTTP_CLIENT_ENGINE)

    private suspend fun ensureHelperInitialized() {
        val contentService = ContentService.getInstance(applicationContext)
        if (!::downloadConnectionHelper.isInitialized) {
            initializationMutex.withLock {
                if (!::downloadConnectionHelper.isInitialized) {
                    val healthCheckUrl = (contentService.downloadMetadata(
                        AppInfoKey(),
                        maxRetries = 5 // Don't block agents infinitely if health check fails
                    ) as AppInfo).globalBaseUrl
                    downloadConnectionHelper = DownloadConnectionHelper(healthCheckUrl)
                }
            }
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
        while (currentCoroutineContext().isActive) {
            val (nextDownload, operations) = queue.receive()
            log.debug("Agent $downloadAgentId Picked ${nextDownload.fileEntryOperation.fileEntry.name} with priority ${nextDownload.priority()}")
            downloadCacheItem(nextDownload, operations)
            yield()
        }
    }

    private suspend fun downloadCacheItem(download: FileCacheItem, operations: List<ContentDownload>) {
        val fileName = download.fileEntryOperation.fileEntry.name
        try {
            ensureHelperInitialized()
            downloadConnectionHelper.retryOnConnectivityFailure({
                operations.forEach { it.notifyBadConnection() }
            }, maxRetries = 10) { // Limit retries for individual files to avoid blocking agents forever
                transformToConnectivityException {
                    httpClient.prepareGet(download.fileEntryOperation.origin!!).execute { response ->
                        when (response.status.value) {
                            in 200..299 -> {
                                val channel = response.bodyAsChannel()
                                val hash = saveFile(download.fileEntryOperation, channel)
                                if (hash != download.fileEntryOperation.fileEntry.sha256) {
                                    val hint = "Hash mismatch on $fileName.\n" +
                                            "Local hash $hash vs remote hash ${download.fileEntryOperation.fileEntry.sha256}"
                                    log.warn(hint)
                                    SentryWrapper.captureMessage(hint, SentryWrapperLevel.WARNING)
                                }
                                download.fileEntryOperation.fileEntry.setDownloadDate(Date(), applicationContext)
                                operations.forEach { it.notifySuccessfulItem() }
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
                                operations.forEach { it.notifyFailedItem(exception) }
                                SentryWrapper.captureException(exception)
                            }
                            in 500..599 -> {
                                val hint =
                                    "Response code ${response.status.value} while trying to download $fileName"
                                log.warn(hint)
                                val exception = ConnectivityException.ServerUnavailableException(hint)
                                operations.forEach { it.notifyFailedItem(exception) }
                                SentryWrapper.captureException(exception)
                            }
                            else -> {
                                val hint = "Unexpected code ${response.status.value} for  $fileName"
                                log.warn(hint)
                                operations.forEach { it.notifyFailedItem(ConnectivityException.ImplementationException(hint)) }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            operations.forEach { it.notifyFailedItem(e) }
            if (e is CancellationException) throw e
            SentryWrapper.captureException(e)
        } finally {
            operations.forEach { it.checkIfItemsCompleteAndNotifyResult(Unit) }
        }
    }

    private suspend fun saveFile(download: FileEntryOperation, channel: ByteReadChannel): String {
        val file = File(download.destination!!)
        file.parentFile?.mkdirs()

        val hash = MessageDigest.getInstance("SHA-256")

        BufferedOutputStream(file.outputStream()).use { fileStream ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) break
                hash.update(buffer, 0, read)
                fileStream.write(buffer, 0, read)
                yield()
            }
            fileStream.flush()
        }

        val digest = hash.digest()
        return digest.joinToString("") { "%02x".format(it) }
    }
}