package de.taz.app.android.ui.home.page

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.moment.MomentView
import kotlinx.coroutines.*
import kotlin.IllegalStateException

interface MomentViewActionListener {
    fun onLongClicked(momentViewData: MomentViewData) = Unit
    fun onImageClicked(momentViewData: MomentViewData) = Unit
    fun onDateClicked(momentViewData: MomentViewData) = Unit
}

class MomentViewDataBinding(
    private val lifecycleOwner: LifecycleOwner,
    private val issuePublication: IssuePublication,
    private val dateFormat: DateFormat,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: MomentViewActionListener
) {
    private val dataService = DataService.getInstance()
    private val feedRepository = FeedRepository.getInstance()
    private val toastHelper = ToastHelper.getInstance()
    private val storageService = StorageService.getInstance()
    private var boundView: MomentView? = null

    private lateinit var momentViewData: MomentViewData

    private var bindJob: Job? = null

    fun bindView(momentView: MomentView) {
        boundView = momentView
        boundView?.setDate(issuePublication.date, dateFormat)

        bindJob = lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            val moment = dataService.getMoment(issuePublication, retryOnFailure = true)
                ?: throw IllegalStateException("Moment for expected publication $issuePublication not found")
            val dimension = feedRepository.get(moment.issueFeedName)
                ?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO

            dataService.ensureDownloaded(moment)

            // refresh moment after download
            val downloadedMoment = dataService.getMoment(issuePublication, retryOnFailure = true) ?: throw IllegalStateException("Issue for expected key $issuePublication not found")
            val momentImageUri = downloadedMoment.getMomentImage()?.let {
                storageService.getFileUri(FileEntry(it))
            }
            val animatedMomentUri = downloadedMoment.getIndexHtmlForAnimated()?.let {
                storageService.getFileUri(it)
            }

            val momentType = if (animatedMomentUri != null) {
                MomentType.ANIMATED
            } else {
                MomentType.STATIC
            }

            val momentUri = when (momentType) {
                MomentType.ANIMATED -> animatedMomentUri
                MomentType.STATIC -> momentImageUri
            }

            momentViewData = MomentViewData(
                IssueKey(moment.issueFeedName, moment.issueDate, moment.issueStatus),
                DownloadStatus.pending,
                momentType,
                momentUri,
                dimension
            )

            withContext(Dispatchers.Main) {
                boundView?.show(momentViewData, dateFormat, glideRequestManager)

                boundView?.setOnImageClickListener {
                    onMomentViewActionListener.onImageClicked(momentViewData)
                }

                boundView?.setOnLongClickListener {
                    onMomentViewActionListener.onLongClicked(momentViewData)
                    true
                }

                boundView?.setOnDateClickedListener {
                    onMomentViewActionListener.onDateClicked(
                        momentViewData
                    )
                }

                boundView?.setOnDownloadClickedListener { onDownloadClicked() }
            }
            dataService.withDownloadLiveData(momentViewData.issueKey) {
                withContext(Dispatchers.Main) {
                    it.observeDistinct(lifecycleOwner) { downloadStatus ->
                        boundView?.setDownloadIconForStatus(
                            downloadStatus
                        )
                    }
                }
            }
        }
    }

    private fun onDownloadClicked() {
        if (::momentViewData.isInitialized) {
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


    fun unbind() {
        val exBoundView = boundView
        boundView = null
        bindJob?.cancel()
        exBoundView?.setOnImageClickListener(null)
        exBoundView?.setOnLongClickListener(null)
        exBoundView?.setOnDownloadClickedListener(null)
        exBoundView?.setOnDateClickedListener(null)
        exBoundView?.resetDownloadIcon()
        exBoundView?.clear(glideRequestManager)
    }
}