package de.taz.app.android.ui.home.page

import androidx.fragment.app.Fragment
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.*


class FrontpageViewBinding(
    private val fragment: Fragment,
    frontpagePublication: FrontpagePublication,
    dateFormat: DateFormat,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener
) : CoverViewBinding(
    fragment,
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
            val frontPage = contentService.downloadMetadata(
                coverPublication,
                // Retry indefinitely
                maxRetries = -1
            ) as Page

            // get pdf front page
            contentService.downloadToCacheIfNotPresent(frontPage)

            // Refresh front page
            val downloadedFrontPage =
                contentService.downloadMetadata(coverPublication) as Page

            val fileEntry = fileEntryRepository.get(downloadedFrontPage.pagePdf.name)
            val pdfMomentFilePath = fileEntry?.let { storageService.getFile(it)?.path }

            // Still need to determine the issueKey, it's not part of a [Page]
            // Therefore we need the [Moment]:
            val moment = contentService.downloadMetadata(
                MomentPublication(
                    coverPublication.feedName,
                    coverPublication.date
                )
            ) as Moment

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
                    withContext(Dispatchers.Main) {
                        fragment.requireActivity()
                            .showIssueDownloadFailedDialog(coverViewData.issueKey)
                    }
                }
            }
        }
    }
}