package de.taz.app.android.ui.share

import android.content.Context
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.data.HTTP_CLIENT_ENGINE
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StoragePathService
import de.taz.app.android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ARTICLE_SHARE_CACHE_PATH = "share"

/**
 * Helper class to handle Article PDF downloads for sharing.
 *
 * The PDFs are downloaded to the Android cache folder and must be deleted regularly by the App.
 * Android only deletes cache files automatically if the system disk is running low on space.
 *
 * [ShareArticleDownloadHelper] is using its custom [HttpClient] to download the PDFs, because the
 * central [FileDownloader] only works for [FileEntry]s stored in the local database.
 * We can't use this mechanism here, because [SearchHit]s might also be shared.
 */
class ShareArticleDownloadHelper(private val applicationContext: Context) {

    class ShareArticleException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private val log by Log

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val storagePathService = StoragePathService.getInstance(applicationContext)

    private val httpClient by lazy {
        HttpClient(HTTP_CLIENT_ENGINE) {
            expectSuccess = true
        }
    }

    /**
     * Download the PDF file for an [ArticleStub] stored in the local database to a cache directory for sharing.
     *
     * @throws ShareArticleException in case of errors
     */
    suspend fun downloadArticlePdf(articleStub: ArticleStub): File {
        val articlePdf = articleStub.pdfFileName?.let { fileEntryRepository.get(it) }
        val issueStub = articleStub.getIssueStub(applicationContext)

        if (issueStub == null || articlePdf == null) {
            throw ShareArticleException("${articleStub.key} (articlePdf=${articlePdf != null}, issueStub=${issueStub != null})")
        }

        val baseUrl = storagePathService.determineBaseUrl(articlePdf, issueStub)
        return downloadArticlePdf(articlePdf.name, baseUrl)
    }

    /**
     * Download the PDF file [articlePdfName] from the [baseUrl] to a cache directory for sharing.
     *
     * @throws ShareArticleException in case of errors
     */
    suspend fun downloadArticlePdf(articlePdfName: String, baseUrl: String): File {
        val shareCacheDir = applicationContext.cacheDir.resolve(ARTICLE_SHARE_CACHE_PATH)
        val articlePdfFile = shareCacheDir.resolve(articlePdfName)
        try {
            val articlePdfUrl = URLBuilder(baseUrl).appendPathSegments(articlePdfName).build()
            download(articlePdfUrl, articlePdfFile)

        } catch (e: Exception) {
            throw ShareArticleException("Could not download Article PDF: $articlePdfName", e)
        }

        return articlePdfFile
    }

    private suspend fun download(articlePdfUrl: Url, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        httpClient.prepareGet(articlePdfUrl).execute { response ->
            target.outputStream().use {
                response.bodyAsChannel().copyTo(it)
            }
        }
    }

    /**
     * Cleanup the cache used for storing Article PDFs for sharing.
     */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        val shareCacheDir = applicationContext.cacheDir.resolve(ARTICLE_SHARE_CACHE_PATH)
        if (!shareCacheDir.deleteRecursively()) {
            log.error("Could not clear the the share cache directory")
        }
    }
}