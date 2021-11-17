package de.taz.app.android.ui.home.page

import androidx.fragment.app.Fragment
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.singletons.*
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.*
import kotlin.IllegalStateException


class MomentViewBinding(
    private val fragment: Fragment,
    momentPublication: MomentPublication,
    dateFormat: DateFormat,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener
) : CoverViewBinding(
    fragment,
    momentPublication,
    dateFormat,
    glideRequestManager,
    onMomentViewActionListener
) {
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData = withContext(Dispatchers.IO) {
        try {
            val moment = contentService.downloadMetadata(
                coverPublication,
                // Retry indefinitely
                maxRetries = -1
            ) as Moment
            val dimension = feedRepository.get(moment.issueFeedName)
                ?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
            try {
                contentService.downloadToCache(moment, priority = DownloadPriority.High)
            } catch (e: CacheOperationFailedException) {
                toastHelper.showConnectionToServerFailedToast()
            }
            // refresh moment after download
            val downloadedMoment =
                contentService.downloadMetadata(coverPublication) as Moment
            val momentImageUri = downloadedMoment.getMomentImage()?.let {
                storageService.getFileUri(FileEntry(it))
            }
            val animatedMomentUri = downloadedMoment.getIndexHtmlForAnimated()?.let {
                storageService.getFileUri(it)
            }

            val momentType = if (animatedMomentUri != null) {
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
                downloadedMoment.issueKey,
                CacheState.ABSENT,
                momentType,
                momentUri,
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
                    contentService.downloadToCache(coverViewData.issueKey)
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