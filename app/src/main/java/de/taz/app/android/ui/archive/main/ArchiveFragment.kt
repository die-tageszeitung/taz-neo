package de.taz.app.android.ui.archive.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.archive.endNavigation.ArchiveEndNavigationFragment
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import kotlinx.android.synthetic.main.fragment_archive.*
import kotlinx.coroutines.launch

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : BaseMainFragment<ArchiveContract.Presenter>(),
    ArchiveContract.View {

    override val endNavigationFragment = ArchiveEndNavigationFragment()

    override val presenter = ArchivePresenter()
    val archiveListAdapter = ArchiveListAdapter(this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)

        fragment_archive_swipe_refresh.setOnRefreshListener {
            lifecycleScope.launch {
                presenter.onRefresh()
            }
        }

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns(context))
        }
        fragment_archive_grid.adapter = archiveListAdapter

        presenter.onViewCreated(savedInstanceState)

        fragment_archive_grid.addOnScrollListener(
            ArchiveOnScrollListener(
                this
            )
        )

    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        (fragment_archive_grid?.adapter as? ArchiveListAdapter)?.setIssueStubs(issueStubs)
    }

    override fun hideRefreshLoadingIcon() {
        fragment_archive_swipe_refresh?.isRefreshing = false
    }

    override fun hideProgressbar(issueStub: IssueStub) {
        archiveListAdapter.apply {
            notifyItemChanged(getItemPosition(issueStub), false)
        }
    }

    override fun showProgressbar(issueStub: IssueStub) {
        archiveListAdapter.apply {
            notifyItemChanged(getItemPosition(issueStub), true)
        }

    }

    private fun calculateNoOfColumns(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        val columnWidthDp =
            resources.getDimension(R.dimen.fragment_archive_item_width) / displayMetrics.density
        return (screenWidthDp / columnWidthDp).toInt()
    }

    override fun setFeeds(feeds: List<Feed>) {
       archiveListAdapter.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        archiveListAdapter.setInactiveFeedNames(inactiveFeedNames)
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }
}