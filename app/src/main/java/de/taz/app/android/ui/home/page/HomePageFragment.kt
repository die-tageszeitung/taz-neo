package de.taz.app.android.ui.home.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

abstract class HomePageFragment(
    layoutID: Int
) : BaseViewModelFragment<HomePageViewModel>(layoutID) {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var issueRepository: IssueRepository
    private lateinit var authHelper: AuthHelper
    private lateinit var feedRepository: FeedRepository

    private var momentChangedListener: MomentChangedListener? = null

    override val viewModel: HomePageViewModel by activityViewModels()

    abstract var adapter: IssueFeedAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val feed = runBlocking(Dispatchers.IO) { feedRepository.get(DISPLAYED_FEED) }
            viewModel.setFeed(feed!!)
        } catch (e: NullPointerException) {
            log.error("Illegal State! This fragment expects $DISPLAYED_FEED feed to be cached in database")
            Sentry.captureException(e)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        feedRepository = FeedRepository.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        momentChangedListener = viewModel.addNotifyMomentChangedListener { date ->
            lifecycleScope.launch(Dispatchers.Main) {
                adapter.notifyItemChanged(adapter.getPosition(date))
            }
        }
        authHelper.authStatusLiveData.observeDistinctIgnoreFirst(viewLifecycleOwner) {
            if (AuthStatus.valid == it) {
                lifecycleScope.launchWhenResumed {
                    withContext(Dispatchers.Main) {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        momentChangedListener?.let {
            viewModel.removeNotifyMomentChangedListener(it)
        }
    }

    fun onItemSelected(issueKey: IssueKey) {
        Intent(requireActivity(), IssueViewerActivity::class.java).apply {
            putExtra(IssueViewerActivity.KEY_ISSUE_KEY, issueKey)
            startActivity(this)
        }
    }

    /**
     * callback function to be used by CoverFlow to apply ZoomTransformations so issues at the left
     * and right are smaller
     */
    open fun callbackWhenIssueIsSet() = Unit

}
