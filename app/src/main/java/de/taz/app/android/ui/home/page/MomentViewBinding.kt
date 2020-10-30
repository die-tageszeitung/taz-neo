package de.taz.app.android.ui.home.page

import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class MomentViewDataBinding(
    private val fragment: HomePageFragment,
    private val issueStubViewData: IssueStubViewData,
    private val dateClickedListener: (issueDate: Date) -> Unit = {},
    private val dateFormat: DateFormat
) {
    private val log by Log

    private val dataService = DataService.getInstance()
    private var boundView: MomentView? = null

    fun bindView(momentView: MomentView) {
        boundView = momentView
        momentView.show(issueStubViewData, dateFormat)
        momentView.setOnImageClickListener {
            fragment.onItemSelected(issueStubViewData.issueStub)
        }

        momentView.setOnLongClickListener { view ->
            log.debug("onLongClickListener triggered for view: $view!")
            fragment.getMainView()?.let { mainView ->
                fragment.showBottomSheet(
                    IssueBottomSheetFragment.create(
                        mainView,
                        issueStubViewData.issueStub
                    )
                )
            }
            true
        }

        momentView.setOnDateClickedListener {
            dateClickedListener(simpleDateFormat.parse(issueStubViewData.issueStub.date)!!)
        }

        momentView.setOnDownloadClickedListener {
            boundView?.setDownloadIconForStatus(DownloadStatus.started)
            CoroutineScope(Dispatchers.IO).launch {
                val issue = issueStubViewData.issueStub.getIssue()
                // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                try {
                    val updatedIssue =
                        dataService.getIssue(
                            issue.issueKey,
                            allowCache = false,
                            saveOnlyIfNewerMoTime = true
                        )
                    updatedIssue?.let {
                        dataService.ensureDownloaded(updatedIssue)
                    }
                } catch (e: ConnectivityException.Recoverable) {
                    ToastHelper.getInstance().showNoConnectionToast()
                }
            }
        }

        dataService.withDownloadLiveDataSync(issueStubViewData.issueStub) {
            it.observeDistinct(fragment) { downloadStatus ->
                boundView?.show(issueStubViewData.copy(downloadStatus = downloadStatus), dateFormat)
            }
        }
    }

    fun unbind() {
        boundView?.setOnImageClickListener(null)
        boundView?.setOnLongClickListener(null)
        boundView?.setOnDownloadClickedListener(null)
        boundView?.setOnDateClickedListener(null)
        boundView?.clear()
        boundView = null
    }
}