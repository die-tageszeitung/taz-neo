package de.taz.app.android.ui.home.page

import androidx.fragment.app.Fragment
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_FILE
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.METADATA_DOWNLOAD_DEFAULT_RETRIES
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*


class FrontpageViewBinding(
    fragment: Fragment,
    frontpagePublication: FrontpagePublication,
    coverViewDate: CoverViewDate?,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener,
    observeDownload: Boolean
) : CoverViewBinding(
    fragment,
    frontpagePublication,
    coverViewDate,
    glideRequestManager,
    onMomentViewActionListener,
    observeDownload
) {

    private val storageService = StorageService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData {
        return try {
            val dimension =
                feedRepository.get(coverPublication.feedName)?.momentRatioAsDimensionRatioString()
                    ?: DEFAULT_MOMENT_RATIO
            val frontPage = contentService.downloadMetadata(
                coverPublication,
                // After 7 retries show the fallback
                maxRetries = METADATA_DOWNLOAD_DEFAULT_RETRIES
            ) as Page

            // get pdf front page
            contentService.downloadToCache(frontPage)

            // Refresh front page
            val downloadedFrontPage =
                contentService.downloadMetadata(coverPublication) as Page

            val fileEntry = fileEntryRepository.get(downloadedFrontPage.pagePdf.name)
            val pdfMomentFilePath = fileEntry?.let { storageService.getFile(it)?.path }

            val momentType = CoverType.FRONT_PAGE
            CoverViewData(
                momentType,
                pdfMomentFilePath,
                dimension
            )
        } catch (e: CacheOperationFailedException) {
            // maxRetries reached - so show the fallback cover view:
            val momentUri = fileEntryRepository.get(DEFAULT_MOMENT_FILE)?.let {
                storageService.getFileUri(it)
            }
            CoverViewData(
                CoverType.STATIC,
                momentUri,
                DEFAULT_MOMENT_RATIO
            )
        }
    }
}