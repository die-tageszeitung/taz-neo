package de.taz.app.android.ui.home.page

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import java.util.*

interface MomentViewActionListener {
    fun onLongClicked(momentViewData: MomentViewData) = Unit
    fun onImageClicked(momentViewData: MomentViewData) = Unit
    fun onDateClicked(momentViewData: MomentViewData) = Unit
}

class MomentViewDataBinding(
    private val lifecycleOwner: LifecycleOwner,
    private val date: Date,
    private val feed: Feed,
    private val dateFormat: DateFormat,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: MomentViewActionListener
) {
    private val log by Log

    protected val dataService = DataService.getInstance()
    private var boundView: MomentView? = null

    protected lateinit var momentViewData: MomentViewData

    private var bindJob: Job? = null

    fun bindView(momentView: MomentView) {


        boundView = momentView
        boundView?.setDate(simpleDateFormat.format(date), dateFormat)

        bindJob?.cancel()
        bindJob = lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            val issueStub =
                dataService.getIssueStub(
                    IssueKey(
                        feed.name,
                        simpleDateFormat.format(date),
                        AuthHelper.getInstance().eligibleIssueStatus
                    ), retryOnFailure = true
                )
            momentViewData = issueStub?.let { issueStub ->
                dataService.getMoment(issueStub.issueKey)?.let { moment ->
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

                    MomentViewData(
                        issueStub,
                        if (issueStub.getDownloadDate() != null) DownloadStatus.done else DownloadStatus.pending,
                        momentType,
                        momentUri,
                        dimension
                    )
                }
            } ?: throw IllegalStateException("Expected an issue at date $date")

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
            dataService.withDownloadLiveData(momentViewData.issueStub) {
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
                val issue = momentViewData.issueStub.getIssue()
                // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                try {
                    val updatedIssue =
                        dataService.getIssue(
                            issue.issueKey,
                            allowCache = false
                        )
                    updatedIssue?.let {
                        dataService.ensureDownloaded(updatedIssue)
                    }
                } catch (e: ConnectivityException.Recoverable) {
                    ToastHelper.getInstance().showNoConnectionToast()
                }
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
        exBoundView?.clear(glideRequestManager)
    }
}