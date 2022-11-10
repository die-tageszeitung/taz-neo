package de.taz.app.android.ui.home.page

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.FeedService
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
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
abstract class IssueFeedFragment<VIEW_BINDING : ViewBinding> :
    BaseViewModelFragment<IssueFeedViewModel, VIEW_BINDING>() {

    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper

    private var momentChangedListener: MomentChangedListener? = null

    override val viewModel: IssueFeedViewModel by activityViewModels()

    abstract var adapter: IssueFeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get feed and propagate it to the viewModel
        lifecycleScope.launch {
            val feedService = FeedService.getInstance(requireContext().applicationContext)

            // Get the latest feed and propagate it if it is valid.
            // Otherwise (null feed) show a warning to the user.
            // Warning: This will re-try to request the feed from the api indefinitely in case of connection failures.
            feedService
                .getFeedFlowByName(BuildConfig.DISPLAYED_FEED, retryOnFailure = true)
                .distinctUntilChanged()
                .collect {
                    if (it != null) {
                        viewModel.setFeed(it)
                    } else {
                        val message =
                            "Failed to retrieve feed ${BuildConfig.DISPLAYED_FEED}, cannot show anything"
                        log.error(message)
                        Sentry.captureMessage(message)
                        toastHelper.showSomethingWentWrongToast()
                    }
                }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // get or initialize the singletons
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // notify the adapter to redraw the item if it changed (e.g. got deleted)
        momentChangedListener = viewModel.addNotifyMomentChangedListener { date ->
            lifecycleScope.launch(Dispatchers.Main) {
                adapter.notifyItemChanged(adapter.getPosition(date))
            }
        }

        // redraw once the user logs in
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


    /**
     * function to open the given issue in the corresponding reading activity
     * @param issuePublication the issue to be opened
     */
    fun onItemSelected(issuePublication: AbstractIssuePublication) {
        requireActivity().apply {
            val intent = if (viewModel.pdfModeLiveData.value == true) {
                PdfPagerActivity.newIntent(this,  IssuePublicationWithPages(issuePublication))
            } else {
                IssueViewerActivity.newIntent(this, IssuePublication(issuePublication))
            }
            startActivity(intent)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        momentChangedListener?.let {
            viewModel.removeNotifyMomentChangedListener(it)
        }
    }

}
