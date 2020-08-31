package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.home.page.HomePageOnScrollListener
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.home.page.HomePageFragment
import kotlinx.android.synthetic.main.fragment_archive.*

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : HomePageFragment(R.layout.fragment_archive) {

    override var adapter: HomePageAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = adapter ?: ArchiveAdapter(
            this,
            R.layout.fragment_archive_item
        )

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns(context))
        }
        fragment_archive_grid.adapter = adapter

        fragment_archive_grid.addOnScrollListener(
            HomePageOnScrollListener(
                this
            )
        )
        fragment_archive_to_cover_flow.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getMainView()?.apply {
            setDefaultDrawerNavButton()
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
        adapter?.setAuthStatus(authStatus)
    }

    override fun setFeeds(feeds: List<Feed>) {
        adapter?.setFeeds(feeds)
    }

    override fun setInactiveFeedNames(feedNames: Set<String>) {
        adapter?.setInactiveFeedNames(feedNames)
    }

    override fun onDestroyView() {
        fragment_archive_grid.adapter = null
        super.onDestroyView()
    }


}
