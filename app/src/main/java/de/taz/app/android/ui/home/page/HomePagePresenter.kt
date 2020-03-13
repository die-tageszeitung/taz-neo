package de.taz.app.android.ui.home.page

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val NUMBER_OF_REQUESTED_MOMENTS = 10

abstract class HomePagePresenter<VIEW : HomePageContract.View>(
    private val apiService: ApiService = ApiService.getInstance(),
    private val dateHelper: DateHelper = DateHelper.getInstance(),
    private val downloadService: DownloadService = DownloadService.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : BasePresenter<VIEW, HomePageDataController>(
    HomePageDataController::class.java
), HomePageContract.Presenter {

    private val log by Log

    private var lastRequestedDate = ""

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.apply {
                    issueStubsLiveData.observeDistinct(
                        it,
                        HomePageIssueStubsObserver(
                            this@HomePagePresenter
                        )
                    )

                    feedsLiveData.observeDistinct(it) { feeds ->
                        view.setFeeds(feeds)
                    }
                    inactiveFeedNameLiveData.observeDistinct(it) { feedNames ->
                        view.setInactiveFeedNames(feedNames)
                    }
                    authStatusLiveData.observeDistinct(it) { authStatus ->
                        view.setAuthStatus(authStatus)
                    }
                }
            }
        }
    }

    override suspend fun onItemSelected(issueStub: IssueStub) {
        getView()?.getMainView()?.showIssue(issueStub)
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

    override fun getCurrentPosition(): Int? {
        return viewModel?.getCurrentPosition()
    }

    override fun setCurrentPosition(position: Int) {
        viewModel?.setCurrentPosition(position)
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
