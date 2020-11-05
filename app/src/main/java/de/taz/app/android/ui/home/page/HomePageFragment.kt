package de.taz.app.android.ui.home.page

import android.content.Context
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log

abstract class HomePageFragment(
    layoutID: Int
) : BaseViewModelFragment<HomePageViewModel>(layoutID) {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var issueRepository: IssueRepository
    private lateinit var authHelper: AuthHelper
    private lateinit var feedRepository: FeedRepository

    abstract var adapter: IssueFeedAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance()
        feedRepository = FeedRepository.getInstance()
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
