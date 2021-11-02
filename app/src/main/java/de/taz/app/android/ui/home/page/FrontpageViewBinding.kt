package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import io.sentry.Sentry
import kotlinx.coroutines.*


class FrontpageViewBinding(
    applicationContext: Context,
    lifecycleOwner: LifecycleOwner,
    private val issuePublication: IssuePublication,
    dateFormat: DateFormat,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener
) : CoverViewBinding(
    applicationContext,
    lifecycleOwner,
    dateFormat,
    glideRequestManager,
    onMomentViewActionListener
) {

    private val storageService = StorageService.getInstance(applicationContext)
    private val dataService = DataService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData = withContext(Dispatchers.IO) {
        val dimension = feedRepository.get(issuePublication.feed)?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
        val frontPage = dataService.getFrontPage(issuePublication, retryOnFailure = true)

            // get pdf front page
        val pdfMomentFilePath = frontPage?.let {
            contentService.downloadToCacheIfNotPresent(frontPage)
            val downloadedFrontPage =
                dataService.getFrontPage(issuePublication, allowCache = true)?.pagePdf
            val fileEntry = downloadedFrontPage?.let { fileEntryRepository.get(it.name) }
            fileEntry?.let { storageService.getFile(it)?.path }
        }

        val momentType = CoverType.FRONT_PAGE
        val issueKey = dataService.determineIssueKeyWithPages(issuePublication)
        CoverViewData(
            issueKey,
            CacheState.ABSENT,
            momentType,
            pdfMomentFilePath,
            dimension
        )
    }

    override fun onDownloadClicked() {
        if (dataInitialized()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentService.downloadToCacheIfNotPresent(coverViewData.issueKey)
                } catch (e: CacheOperationFailedException) {
                    // Pass the exception, if that process wen wrong the download indicator should reset,
                    // and a toast is being shown in the observer in CoverViewBinding
                    Sentry.captureException(e, "Something went wrong when downloading ${coverViewData.issueKey}")
                }
            }
        }
    }
}