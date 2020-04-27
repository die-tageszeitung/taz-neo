package de.taz.app.android.ui.home.page

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.ViewModelBaseMainFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val NUMBER_OF_REQUESTED_MOMENTS = 10

abstract class HomePagePresenter(
    private val layoutID : Int,
    private val apiService: ApiService = ApiService.getInstance(),
    private val dateHelper: DateHelper = DateHelper.getInstance(),
    private val downloadService: DownloadService = DownloadService.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : ViewModelBaseMainFragment(layoutID) {

    private val log by Log

    private var lastRequestedDate = ""

    private var viewModel : HomePageViewModel? = null

    abstract fun setFeeds(feeds: List<Feed>)
    abstract fun setInactiveFeedNames(feedNames: Set<String>)
    abstract fun setAuthStatus(authStatus: AuthStatus)
    abstract fun onDataSetChanged(issueStubs: List<IssueStub>)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = viewModel ?: HomePageViewModel(requireActivity().applicationContext)

        viewModel?.apply {
            issueStubsLiveData.observeDistinct(
                viewLifecycleOwner,
                HomePageIssueStubsObserver(
                    this@HomePagePresenter
                )
            )

            feedsLiveData.observeDistinct(viewLifecycleOwner) { feeds ->
                setFeeds(feeds)
            }
            inactiveFeedNameLiveData.observeDistinct(viewLifecycleOwner) { feedNames ->
                setInactiveFeedNames(feedNames)
            }
            authStatusLiveData.observeDistinct(viewLifecycleOwner) { authStatus ->
                setAuthStatus(authStatus)
            }
        }
    }

    fun onItemSelected(issueStub: IssueStub) {
        showIssue(issueStub)
    }

    suspend fun getNextIssueMoments(date: String) {
        if (lastRequestedDate.isEmpty() || date <= lastRequestedDate) {
            log.debug("lastRequestedDate: $lastRequestedDate date: $date requested new issues")
            lastRequestedDate = dateHelper.stringToStringWithDelta(
                date, -NUMBER_OF_REQUESTED_MOMENTS
            ) ?: ""

            viewLifecycleOwner.lifecycleScope.launch {
                downloadNextIssues(
                    date,
                    NUMBER_OF_REQUESTED_MOMENTS
                )
            }
        }
    }

    fun getCurrentPosition(): Int? {
        return viewModel?.getCurrentPosition()
    }

    fun setCurrentPosition(position: Int) {
        viewModel?.setCurrentPosition(position)
    }

    suspend fun downloadNextIssues(date: String, limit: Int) {
        withContext(Dispatchers.IO) {
            try {
                val issues = apiService.getIssuesByDate(issueDate = date, limit = limit)
                issueRepository.save(issues)
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                ToastHelper.getInstance(activity?.applicationContext)
                    .showToast(R.string.toast_no_internet)
            }
        }
    }

}
