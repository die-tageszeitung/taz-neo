package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.home.page.HomePageFragment
import kotlinx.android.synthetic.main.fragment_archive.*

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : HomePageFragment(R.layout.fragment_archive) {

    val archiveListAdapter =
        ArchiveAdapter(
            this,
            R.layout.fragment_archive_item
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns(context))
        }
        fragment_archive_grid.adapter = archiveListAdapter

        fragment_archive_grid.addOnScrollListener(
            ArchiveOnScrollListener(
                this
            )
        )

    }

    override fun onResume() {
        super.onResume()
        getMainView()?.apply {
            setDrawerNavButton()
            setActiveDrawerSection(RecyclerView.NO_POSITION)
        }
    }

    fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        (fragment_archive_grid?.adapter as? HomePageAdapter)?.setIssueStubs(issueStubs)
    }

    private fun calculateNoOfColumns(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        val columnWidthDp =
            resources.getDimension(R.dimen.fragment_archive_item_width) / displayMetrics.density
        return (screenWidthDp / columnWidthDp).toInt()
    }

    override fun setAuthStatus(authStatus: AuthStatus) {
        archiveListAdapter.setAuthStatus(authStatus)
    }

    override fun setFeeds(feeds: List<Feed>) {
       archiveListAdapter.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        archiveListAdapter.setInactiveFeedNames(inactiveFeedNames)
    }

}
