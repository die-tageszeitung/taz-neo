package de.taz.app.android.ui.home.page

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ConnectionStatusHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment
import de.taz.app.android.ui.showNoInternetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper
    private lateinit var contentService: ContentService

    private var momentChangedListener: MomentChangedListener? = null
    private var lastRefreshMs = 0L

    override val viewModel: IssueFeedViewModel by activityViewModels()

    protected var adapter: IssueFeedAdapter? = null
        set(value) {
            if (field != value) {
                setupNewAdapter(value)
            }
            field = value
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // get or initialize the singletons
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // notify the adapter to redraw the item if it changed (e.g. got deleted)
        momentChangedListener = viewModel.addNotifyMomentChangedListener { date ->
            lifecycleScope.launch(Dispatchers.Main) {
                adapter?.apply {
                    val position = getPosition(date)
                    notifyItemChanged(position)
                }
            }
        }

        // redraw once the user state changes as this might result in the need to re-load the moments
        var prevAuthStatus: AuthStatus? = null
        authHelper.status.asFlow()
            .flowWithLifecycle(lifecycle)
            .filter { prevAuthStatus != it }
            .onEach {
                if (prevAuthStatus != null) {
                    adapter?.notifyDataSetChanged()
                }
                prevAuthStatus = it
            }.launchIn(lifecycleScope)

        // Redraw the feed if some moment placeholders are shown because of connection errors
        viewModel.forceRefreshTimeMs
            .flowWithLifecycle(lifecycle)
            .filter { it > lastRefreshMs }
            .onEach { adapter?.notifyDataSetChanged() }
            .launchIn(lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
        momentChangedListener?.let {
            viewModel.removeNotifyMomentChangedListener(it)
        }
    }


    /**
     * function to open the given issue in the corresponding reading activity
     * @param issuePublication the issue to be opened
     */
    fun onItemSelected(issuePublication: AbstractIssuePublication) {
        val isPdf = viewModel.getPdfMode()
        val isOnline = ConnectionStatusHelper.isOnline(requireContext())
        lifecycleScope.launch {
            requireActivity().apply {
                val fragment = if (isPdf) {
                    PdfPagerWrapperFragment.newInstance(IssuePublicationWithPages(issuePublication))
                } else {
                    IssueViewerWrapperFragment.newInstance(IssuePublication(issuePublication))
                }
                if (getIsDownloaded(issuePublication) || isOnline) {
                    supportFragmentManager.commit {
                        add(R.id.main_content_fragment_placeholder, fragment)
                        addToBackStack(null)
                    }
                } else {
                    showNoInternetDialog()
                }
            }
        }
    }

    private suspend fun getIsDownloaded(issuePublication: AbstractIssuePublication): Boolean {
        val isPdf = viewModel.getPdfMode()
        val abstractIssuePublication = if (isPdf) {
            IssuePublicationWithPages(issuePublication)
        } else {
            issuePublication
        }

        return contentService.isPresent(abstractIssuePublication)
    }

    private fun setupNewAdapter(adapter: IssueFeedAdapter?) {
        updateLastRefreshTime()
        adapter?.registerAdapterDataObserver(onDataSetChangedObserver)
    }

    private val onDataSetChangedObserver = object : RecyclerView.AdapterDataObserver() {
        // Called after adapter.notifyDataSetChanged
        override fun onChanged() {
            updateLastRefreshTime()
        }
    }

    private fun updateLastRefreshTime() {
        lastRefreshMs = System.currentTimeMillis()
    }
}
