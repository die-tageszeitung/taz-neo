package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.*
import kotlinx.coroutines.*
import kotlin.IllegalStateException


class MomentViewBinding(
    private val applicationContext: Context,
    private val lifecycleOwner: LifecycleOwner,
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
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val dataService = DataService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData = withContext(Dispatchers.IO) {
        val moment = dataService.getMoment(issuePublication, retryOnFailure = true)
            ?: throw IllegalStateException("Moment for expected publication $issuePublication not found")
        val dimension = feedRepository.get(moment.issueFeedName)
            ?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO

        contentService.downloadToCacheIfNotPresent(moment)

        // refresh moment after download
        val downloadedMoment = dataService.getMoment(issuePublication, retryOnFailure = true)
            ?: throw IllegalStateException("Moment for expected key $issuePublication not found")
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
            dataService.determineIssueKey(issuePublication),
            CacheState.ABSENT,
            momentType,
            momentUri,
            dimension
        )
    }


    override fun onDownloadClicked() {
        if (dataInitialized()) {
            CoroutineScope(Dispatchers.IO).launch {
                contentService.downloadToCacheIfNotPresent(coverViewData.issueKey)
            }
        }
    }
}