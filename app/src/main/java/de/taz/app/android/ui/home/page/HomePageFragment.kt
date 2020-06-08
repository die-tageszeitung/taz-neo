package de.taz.app.android.ui.home.page

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val NUMBER_OF_REQUESTED_MOMENTS = 10

abstract class HomePageFragment(
    layoutID: Int
) : BaseViewModelFragment<HomePageViewModel>(layoutID) {

    private val log by Log

    private var apiService: ApiService? = null
    private var dateHelper: DateHelper? = null
    private var issueRepository: IssueRepository? = null

    private var lastRequestedDate = ""

    abstract fun setFeeds(feeds: List<Feed>)
    abstract fun setInactiveFeedNames(feedNames: Set<String>)
    abstract fun setAuthStatus(authStatus: AuthStatus)
    abstract fun onDataSetChanged(issueStubs: List<IssueStub>)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        dateHelper = DateHelper.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.apply {
            issueStubsLiveData.observeDistinct(
                viewLifecycleOwner,
                HomePageIssueStubsObserver(
                    this@HomePageFragment
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
            lastRequestedDate = dateHelper?.stringToStringWithDelta(
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

    suspend fun downloadNextIssues(date: String, limit: Int) {
        withContext(Dispatchers.IO) {
            try {
                apiService?.getIssuesByDate(issueDate = date, limit = limit)?.let {
                    issueRepository?.save(it)
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                ToastHelper.getInstance(activity?.applicationContext)
                    .showToast(R.string.toast_no_internet)
            }
        }
    }

}
