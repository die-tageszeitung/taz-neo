package de.taz.app.android.ui.home.page

import androidx.fragment.app.Fragment
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_FILE
import de.taz.app.android.METADATA_DOWNLOAD_DEFAULT_RETRIES
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Moment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.singletons.StorageService


class MomentViewBinding(
    fragment: Fragment,
    momentPublication: MomentPublication,
    coverViewDate: CoverViewDate,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener,
    observeDownload: Boolean,
) : CoverViewBinding(
    fragment,
    momentPublication,
    coverViewDate,
    glideRequestManager,
    onMomentViewActionListener,
    observeDownload
) {
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData {
        return try {
            val moment = contentService.downloadMetadata(
                coverPublication,
                // After 7 retries show the fallback
                maxRetries = METADATA_DOWNLOAD_DEFAULT_RETRIES
            ) as Moment
            contentService.downloadToCache(moment, priority = DownloadPriority.High)

            // Refresh the Moment from the db after download
            val downloadedMoment = contentService.downloadMetadata(coverPublication) as Moment

            val momentImageUri = downloadedMoment.getMomentImage()?.let {
                storageService.getFileUri(FileEntry(it))
            }
            val animatedMomentUri = downloadedMoment.getIndexHtmlForAnimated()?.let {
                storageService.getFileUri(it)
            }
            // Get the setting if user wants to show animated moments (default is true)
            val settingsShowAnimatedMoments =
                GeneralDataStore.getInstance(applicationContext).showAnimatedMoments.get()

            val momentType = if (animatedMomentUri != null && settingsShowAnimatedMoments) {
                CoverType.ANIMATED
            } else {
                CoverType.STATIC
            }


            val momentUri = when (momentType) {
                CoverType.ANIMATED -> animatedMomentUri
                CoverType.STATIC -> momentImageUri
                else -> throw IllegalStateException("MomentViewDataBinding only supports ANIMATED and STATIC")
            }

            CoverViewData(
                momentType,
                momentUri,
                moment.dateDownload
            )

        } catch (e: CacheOperationFailedException) {
            // maxRetries reached - so show the fallback cover view:
            val moment = fileEntryRepository.get(DEFAULT_MOMENT_FILE)
            val momentUri = moment?.let {
                storageService.getFileUri(it)
            }
            CoverViewData(
                CoverType.STATIC,
                momentUri,
                moment?.dateDownload
            )
        }
    }
}