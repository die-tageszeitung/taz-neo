package de.taz.app.android.ui.archive

import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArchivePresenter : BasePresenter<ArchiveContract.View, ArchiveDataController>(
    ArchiveDataController::class.java
), ArchiveContract.Presenter {

    private var feedName = "taz"

    private val apiService = ApiService.getInstance()
    private val issueRepository = IssueRepository.getInstance()

    override fun onViewCreated() {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.observeIssues(it) { issues ->
                    view.onDataSetChanged(issues ?: emptyList())
                }
            }
        }
    }

    override fun onRefresh() {
        // TODO check for new issues and download
        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            val todaysIssue = apiService.getIssueByFeedAndDate(feedName)
            if(!issueRepository.exists(todaysIssue)) {
                issueRepository.save(todaysIssue)
                getView()?.getMainView()?.getApplicationContext()?.let {
                    DownloadService.download(it, todaysIssue.moment)
                }
            }
            getView()?.hideScrollView()
        } ?: getView()?.hideScrollView()
    }

    override fun onScroll() {
       // TODO who knows what
    }
}