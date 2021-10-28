package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.*
import kotlinx.coroutines.*


class FrontpageViewBinding(
    applicationContext: Context,
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

    private val storageService = StorageService.getInstance(applicationContext)
    private val dataService = DataService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)

    override suspend fun prepareData(): CoverViewData = withContext(Dispatchers.IO) {
        val dimension = feedRepository.get(issuePublication.feed)?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
        val frontPage = dataService.getFrontPage(issuePublication, retryOnFailure = true)

            // get pdf front page
        val pdfMomentFilePath = frontPage?.let {
            dataService.ensureDownloaded(frontPage)
            val downloadedFrontPage =
                dataService.getFrontPage(issuePublication, allowCache = true)?.pagePdf
            val fileEntry = downloadedFrontPage?.let { fileEntryRepository.get(it.name) }
            fileEntry?.let { storageService.getFile(it)?.path }
        }

        val momentType = CoverType.FRONT_PAGE
        val issueKey = dataService.determineIssueKeyWithPages(issuePublication)
        CoverViewData(
            issueKey,
            DownloadStatus.pending,
            momentType,
            pdfMomentFilePath,
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
                // TODO: We need to account for loading pdf issues instead of the whole issue atm
                // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                val issue = dataService.getIssue(
                    issuePublication,
                    retryOnFailure = true,
                    allowCache = false,
                    forceUpdate = true,
                    onConnectionFailure = { onConnectionFailure() },
                    cacheWithPages = true
                )
                dataService.ensureDownloaded(
                    collection = IssueWithPages(issue),
                    onConnectionFailure = { onConnectionFailure() }
                )
            }
        }
    }
}