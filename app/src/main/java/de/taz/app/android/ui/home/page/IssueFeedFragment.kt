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
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

abstract class IssueFeedFragment(
    layoutID: Int
) : BaseViewModelFragment<IssueFeedViewModel>(layoutID) {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var dataService: DataService
    private lateinit var authHelper: AuthHelper
    private lateinit var feedRepository: FeedRepository
    private lateinit var toastHelper: ToastHelper

    private var momentChangedListener: MomentChangedListener? = null

    override val viewModel: IssueFeedViewModel by activityViewModels()

    abstract var adapter: IssueFeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val feed = runBlocking(Dispatchers.IO) { dataService.getFeedByName(DISPLAYED_FEED, retryOnFailure = true) }
            viewModel.setFeed(feed!!)
        } catch (e: NullPointerException) {
            log.error("Failed to retrieve feed $DISPLAYED_FEED, cannot show anything")
            Sentry.captureException(e)
            toastHelper.showSomethingWentWrongToast()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        dataService = DataService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance()
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
        authHelper.status.asLiveData().observeDistinctIgnoreFirst(viewLifecycleOwner) {
            if (AuthStatus.valid == it) {
                lifecycleScope.launchWhenResumed {
                    withContext(Dispatchers.Main) {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }


    fun onItemSelected(issueKey: AbstractIssueKey) {
        val (viewerActivityClass, extraKeyIssue) = if (viewModel.pdfMode.value == true) {
            PdfPagerActivity::class.java to PdfPagerActivity.KEY_ISSUE_KEY
        } else {
            IssueViewerActivity::class.java to IssueViewerActivity.KEY_ISSUE_KEY
        }
        Intent(requireActivity(), viewerActivityClass).apply {
            putExtra(extraKeyIssue, issueKey)
            startActivity(this)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        momentChangedListener?.let {
            viewModel.removeNotifyMomentChangedListener(it)
        }
    }

    /**
     * callback function to be used by CoverFlow to apply ZoomTransformations so issues at the left
     * and right are smaller
     */
    open fun callbackWhenIssueIsSet() = Unit

}
