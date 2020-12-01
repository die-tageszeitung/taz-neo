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
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.util.Log
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
    private val onMomentViewActionListener: MomentViewActionListener,
) {
    private val log by Log

    private val dataService = DataService.getInstance()
    private var boundView: MomentView? = null

    private lateinit var momentViewData: MomentViewData
    private lateinit var issueKey: IssueKey

    private var bindJob: Job? = null

    fun bindView(momentView: MomentView) {
        boundView = momentView
        boundView?.setDate(issuePublication.date, dateFormat)

        bindJob = lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            issueKey = dataService.determineIssueKey(issuePublication)

            val moment = dataService.getMoment(issueKey, retryOnFailure = true)
                ?: throw IllegalStateException("Issue for expected key $issueKey not found")
            val dimension = FeedRepository.getInstance().get(moment.issueFeedName)
                ?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
            val momentImageUri = moment.getMomentImage()?.let {
                FileHelper.getInstance().getAbsoluteFilePath(FileEntry(it))
            }
            val animatedMomentUri = moment.getIndexHtmlForAnimated()?.let {
                FileHelper.getInstance().getAbsoluteFilePath(it)
            }
            dataService.ensureDownloaded(moment)

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
                issueKey,
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

    fun onDownloadClicked() {
        if (::momentViewData.isInitialized) {
            boundView?.setDownloadIconForStatus(DownloadStatus.started)
            CoroutineScope(Dispatchers.IO).launch {
                // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                val issue = dataService.getIssue(
                        issueKey,
                        retryOnFailure = true,
                        allowCache = false
                    ) ?: throw IllegalStateException("No issue found for $issueKey")
                dataService.ensureDownloaded(issue)
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