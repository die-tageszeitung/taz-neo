package de.taz.app.android.ui.home.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Abstract class for Fragment which has an IssueFeed to show - e.g.
 * [de.taz.app.android.ui.home.page.coverflow.CoverflowFragment] or
 * [de.taz.app.android.ui.home.page.archive.ArchiveFragment]
 *
 * To use this class the abstract [adapter] needs to be overwritten.
 * This class takes care of getting the feed, setting it on the ViewModel and notifying the
 * [adapter] if an item has changed or the user has logged in.
 *
 * Additionally it offers a function to open an issue in the correct reader application
 * see [onItemSelected]
 *
 */
abstract class IssueFeedFragment(
    layoutID: Int
) : BaseViewModelFragment<IssueFeedViewModel>(layoutID) {

    private val log by Log

    private lateinit var dataService: DataService
    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper

    private var momentChangedListener: MomentChangedListener? = null

    override val viewModel: IssueFeedViewModel by activityViewModels()

    abstract var adapter: IssueFeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get feed and propagate it to the viewModel
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val feed = dataService.getFeedByName(
                    DISPLAYED_FEED,
                    retryOnFailure = true
                )
                withContext(Dispatchers.Main) {
                    viewModel.setFeed(feed!!)
                }
            } catch (e: NullPointerException) {
                log.error("Failed to retrieve feed $DISPLAYED_FEED, cannot show anything")
                Sentry.captureException(e)
                toastHelper.showSomethingWentWrongToast()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // get or initialize the singletons
        dataService = DataService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // notify the adapter to redraw the item if it changed (e.g. got deleted)
        momentChangedListener = viewModel.addNotifyMomentChangedListener { date ->
            adapter.notifyItemChanged(adapter.getPosition(date))
        }

        // redraw once the user logs in
        authHelper.status.asLiveData().observeDistinctIgnoreFirst(viewLifecycleOwner) {
            if (AuthStatus.valid == it) {
                adapter.notifyDataSetChanged()
            }
        }
    }


    /**
     * function open the given issue in the corresponding reading activity
     * @param issuePublication the issue to be opened
     */
    fun onItemSelected(issuePublication: AbstractIssuePublication) {
        val (viewerActivityClass, extraKeyIssue) = if (viewModel.pdfModeLiveData.value == true) {
            PdfPagerActivity::class.java to PdfPagerActivity.KEY_ISSUE_PUBLICATION
        } else {
            IssueViewerActivity::class.java to IssueViewerActivity.KEY_ISSUE_PUBLICATION
        }
        Intent(requireActivity(), viewerActivityClass).apply {
            putExtra(extraKeyIssue, issuePublication)
            startActivity(this)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        momentChangedListener?.let {
            viewModel.removeNotifyMomentChangedListener(it)
        }
    }

}
