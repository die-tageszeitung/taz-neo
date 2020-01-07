package de.taz.app.android.ui.home.page

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.DateHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val NUMBER_OF_REQUESTED_MOMENTS = 10

abstract class HomePagePresenter<VIEW: HomePageContract.View>(
    private val apiService: ApiService = ApiService.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance(),
    private val dateHelper: DateHelper = DateHelper.getInstance()
) : BasePresenter<VIEW, HomePageDataController>(
    HomePageDataController::class.java
), HomePageContract.Presenter {

    private val log by Log

    private var lastRequestedDate = ""

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.apply {
                    observeIssueStubs(it,
                        HomePageIssueStubsObserver(
                            this@HomePagePresenter
                        )
                    )
                    observeFeeds(it) { feeds ->
                        view.setFeeds(feeds ?: emptyList())
                    }
                    observeInactiveFeedNames(it) { feedNames ->
                        view.setInactiveFeedNames(feedNames)
                    }
                    observeAuthStatus(it) { authStatus ->
                        view.setAuthStatus(authStatus)
                    }
                }
            }
        }

    }

    override suspend fun onItemSelected(issueStub: IssueStub) {
        getView()?.apply {
            withContext(Dispatchers.IO) {
                val issue = issueRepository.getIssue(issueStub)

                getView()?.getMainView()?.apply {
                    // start download if not yet downloaded
                    if (!issue.isDownloadedOrDownloading()) {
                        getApplicationContext().let { applicationContext ->
                            DownloadService.download(applicationContext, issue)
                        }
                    }

                    // set main issue
                    getMainDataController().setIssueOperations(issueStub)

                    issue.sectionList.first().let { firstSection ->
                        showInWebView(firstSection)
                    }
                }
            }
        }
    }

    override suspend fun getNextIssueMoments(date: String) {
        if (lastRequestedDate.isEmpty() || date <= lastRequestedDate) {
            log.debug("lastRequestedDate: $lastRequestedDate date: $date requested new issues")
            lastRequestedDate = dateHelper.stringToStringWithDelta(
                date, -NUMBER_OF_REQUESTED_MOMENTS
            ) ?: ""

            getView()?.getLifecycleOwner()?.lifecycleScope?.launch {
                downloadNextIssues(
                    date,
                    NUMBER_OF_REQUESTED_MOMENTS
                )
            }
        }
    }

    suspend fun downloadNextIssues(date: String, limit: Int) {
        withContext(Dispatchers.IO) {
            val mainView = getView()?.getMainView()

            try {
                val issues = apiService.getIssuesByDate(issueDate = date, limit = limit)
                issueRepository.save(issues)
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                mainView?.showToast(R.string.toast_no_internet)
            }
        }
    }
}
