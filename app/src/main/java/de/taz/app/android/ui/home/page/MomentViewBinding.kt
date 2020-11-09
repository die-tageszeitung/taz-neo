package de.taz.app.android.ui.home.page

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
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import java.util.*

class MomentViewDataBinding(
    private val fragment: HomePageFragment,
    private val date: Date,
    private val dateClickedListener: (issueDate: Date) -> Unit = {},
    private val dateFormat: DateFormat
) {
    private val log by Log

    private val dataService = DataService.getInstance()
    private var boundView: MomentView? = null

    private var bindJob: Job? = null

    fun bindView(momentView: MomentView, feed: Feed) {
        log.verbose("Start binding $date to ${momentView.hashCode()}")
        val lastJob = bindJob
        bindJob = CoroutineScope(Dispatchers.Main).launch {
            lastJob?.cancelAndJoin()
            log.verbose("Cancelled old bind job and started new $date to ${momentView.hashCode()}")
            boundView = momentView

            boundView?.setDate(simpleDateFormat.format(date), dateFormat)


            val momentViewData = withContext(Dispatchers.IO) {
                val issueStub =
                    dataService.getIssueStub(
                        IssueKey(
                            feed.name,
                            simpleDateFormat.format(date),
                            AuthHelper.getInstance().eligibleIssueStatus
                        ), retryOnFailure = true
                    )
                issueStub?.let { issueStub ->
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
            }
            boundView?.show(momentViewData, dateFormat)

            boundView?.setOnImageClickListener {
                fragment.onItemSelected(momentViewData.issueStub)
            }

            boundView?.setOnLongClickListener { view ->
                log.debug("onLongClickListener triggered for view: $view!")
                fragment.getMainView()?.let { mainView ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val issueStubRefresh =
                            dataService.getIssueStub(issueKey = momentViewData.issueStub.issueKey)
                        issueStubRefresh?.let {
                            withContext(Dispatchers.Main) {
                                fragment.showBottomSheet(
                                    IssueBottomSheetFragment.create(
                                        mainView,
                                        it
                                    )
                                )
                            }
                        }
                    }

                }
                true
            }

            boundView?.setOnDateClickedListener {
                dateClickedListener(simpleDateFormat.parse(momentViewData.issueStub.date)!!)
            }

            boundView?.setOnDownloadClickedListener {
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

            dataService.withDownloadLiveDataSync(momentViewData.issueStub) {
                it.observeDistinct(fragment) { downloadStatus ->
                    boundView?.show(
                        momentViewData.copy(downloadStatus = downloadStatus),
                        dateFormat
                    )
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
        exBoundView?.clear()
    }
}