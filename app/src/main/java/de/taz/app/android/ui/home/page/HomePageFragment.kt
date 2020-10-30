package de.taz.app.android.ui.home.page

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToDownloadIssueHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class HomePageFragment(
    layoutID: Int
) : BaseViewModelFragment<HomePageViewModel>(layoutID) {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var issueRepository: IssueRepository
    private lateinit var toDownloadIssueHelper: ToDownloadIssueHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var feedRepository: FeedRepository

    abstract var adapter: IssueFeedPagingAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        toDownloadIssueHelper = ToDownloadIssueHelper.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance()
        feedRepository = FeedRepository.getInstance()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {

            // TODO: We'll handle multiple of those in the future
            val feed = feedRepository.getAll().first()
            val status =
                if (authHelper.authStatus == AuthStatus.valid) IssueStatus.regular else IssueStatus.public
            viewModel.getPagerForFeed(feed, status).collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    fun onItemSelected(issueStub: IssueStub) {
        showIssue(issueStub)
    }

    /**
     * callback function to be used by CoverFlow to apply ZoomTransformations so issues at the left
     * and right are smaller
     */
    open fun callbackWhenIssueIsSet() = Unit

}
