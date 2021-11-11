package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Page
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
    frontpagePublication: FrontpagePublication,
    dateFormat: DateFormat,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener
) : CoverViewBinding(
    applicationContext,
    lifecycleOwner,
    frontpagePublication,
    dateFormat,
    glideRequestManager,
    onMomentViewActionListener
) {

    private val storageService = StorageService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData = withContext(Dispatchers.IO) {
        try {
            val dimension =
                feedRepository.get(coverPublication.feedName)?.momentRatioAsDimensionRatioString()
                    ?: DEFAULT_MOMENT_RATIO
            val frontPage = contentService.downloadMetadataIfNotPresent(
                coverPublication,
                // Retry indefinitely
                maxRetries = -1
            ) as Page

            // get pdf front page
            contentService.downloadToCacheIfNotPresent(frontPage)

            // Refresh front page
            val downloadedFrontPage = contentService.downloadMetadataIfNotPresent(coverPublication) as Page

            val fileEntry = fileEntryRepository.get(downloadedFrontPage.pagePdf.name)
            val pdfMomentFilePath = fileEntry?.let { storageService.getFile(it)?.path }

            // Still need to determine the issueKey, it's not part of a [Page]
            val moment = contentService.downloadMetadataIfNotPresent(
                MomentPublication(
                    coverPublication.feedName,
                    coverPublication.date
                )) as Moment

            val momentType = CoverType.FRONT_PAGE
            CoverViewData(
                IssueKeyWithPages(moment.issueKey),
                CacheState.ABSENT,
                momentType,
                pdfMomentFilePath,
                dimension
            )
        } catch (e: CacheOperationFailedException) {
            val hint =
                "Error downloading metadata or cover content while binding cover for $coverPublication"
            throw CoverBindingException(hint, e)
        }
    }

    override fun onDownloadClicked() {
        if (dataInitialized()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentService.downloadToCacheIfNotPresent(coverViewData.issueKey)
                } catch (e: CacheOperationFailedException) {
                    // Pass the exception, if that process wen wrong the download indicator should reset,
                    // and a toast is being shown in the observer in CoverViewBinding
                    Sentry.captureException(
                        e,
                        "Something went wrong when downloading ${coverViewData.issueKey}"
                    )
                }
            }
        }
    }
}