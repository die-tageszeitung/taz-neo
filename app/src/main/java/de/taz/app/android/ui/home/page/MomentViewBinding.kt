package de.taz.app.android.ui.home.page

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.cover.MomentView
import kotlinx.coroutines.*
import kotlin.IllegalStateException


class MomentViewBinding(
    private val lifecycleOwner: LifecycleOwner,
    private val issuePublication: IssuePublication,
    dateFormat: DateFormat,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: CoverViewActionListener
) : CoverViewBinding<MomentView>(
    lifecycleOwner,
    issuePublication,
    dateFormat,
    glideRequestManager,
    onMomentViewActionListener
) {
    private val feedRepository = FeedRepository.getInstance()
    private val toastHelper = ToastHelper.getInstance()
    private val storageService = StorageService.getInstance()
    private val dataService = DataService.getInstance()

    override suspend fun prepareData(): CoverViewData = withContext(Dispatchers.IO) {
        val moment = dataService.getMoment(issuePublication, retryOnFailure = true)
            ?: throw IllegalStateException("Moment for expected publication $issuePublication not found")
        val dimension = feedRepository.get(moment.issueFeedName)
            ?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO

        dataService.ensureDownloaded(moment)

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
            DownloadStatus.pending,
            momentType,
            momentUri,
            dimension
        )
    }


    override fun onDownloadClicked() {
        if (dataInitialized()) {
            boundView?.setDownloadIconForStatus(DownloadStatus.started)
            var noConnectionShown = false
            fun onConnectionFailure() {
                if (!noConnectionShown) {
                    lifecycleOwner.lifecycleScope.launch {
                        toastHelper.showNoConnectionToast()
                        noConnectionShown = true
                    }
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                val issue = dataService.getIssue(
                    issuePublication,
                    retryOnFailure = true,
                    allowCache = false,
                    onConnectionFailure = { onConnectionFailure() }
                )
                dataService.ensureDownloaded(
                    collection = issue,
                    onConnectionFailure = { onConnectionFailure() }
                )
            }
        }
    }
}