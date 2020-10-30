package de.taz.app.android.ui.home.page

import android.content.Context
import android.os.Bundle
import android.view.View
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToDownloadIssueHelper
import de.taz.app.android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

const val NUMBER_OF_REQUESTED_MOMENTS = 10

abstract class HomePageFragment(
    layoutID: Int
) : BaseViewModelFragment<HomePageViewModel>(layoutID) {

    private val log by Log

    private var apiService: ApiService? = null

    private var issueRepository: IssueRepository? = null
    private var toDownloadIssueHelper: ToDownloadIssueHelper? = null

    private var lastRequestedDate = ""
    private var lastRequestDateIsRunning = AtomicBoolean(false)

    abstract fun setFeeds(feeds: List<Feed>)
    abstract fun setInactiveFeedNames(feedNames: Set<String>)
    abstract fun setAuthStatus(authStatus: AuthStatus)
    abstract fun onDataSetChanged(issueStubs: List<IssueStub>)

    abstract var adapter: HomePageAdapter?

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        toDownloadIssueHelper = ToDownloadIssueHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

    fun getNextIssueMoments(date: String) {
        if (!lastRequestDateIsRunning.getAndSet(true)) {
            log.debug("lastRequestedDate: $lastRequestedDate date: $date")
            if (lastRequestedDate.isEmpty() || date <= lastRequestedDate) {
                log.debug("lastRequestedDate: requested new issues")

                val requestDate = DateHelper.stringToStringWithDelta(
                    date, -NUMBER_OF_REQUESTED_MOMENTS
                ) ?: ""

                lastRequestedDate = requestDate
                toDownloadIssueHelper?.startMissingDownloads(requestDate)
            }
            lastRequestDateIsRunning.set(false)
        }
    }

    /**
     * callback function to be used by CoverFlow to apply ZoomTransformations so issues at the left
     * and right are smaller
     */
    open fun callbackWhenIssueIsSet() = Unit

}
