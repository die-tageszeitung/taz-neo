package de.taz.app.android.ui.home.page.archive

import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [ArchiveOnScrollListener] ensures endless scrolling is possible
 * if there aren't enough next items in the GridLayoutManager they will be downloaded
 */
class ArchiveOnScrollListener(
    private val archiveFragment: ArchiveFragment
) : RecyclerView.OnScrollListener() {

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        val layoutManager = recyclerView.layoutManager as GridLayoutManager
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        val visibleItemCount = lastVisibleItem - firstVisibleItem
        val totalItemCount = layoutManager.itemCount

        if (lastVisibleItem > totalItemCount - 2 * visibleItemCount) {
            archiveFragment.archiveListAdapter.getItem(totalItemCount - 1)?.date?.let { date ->
                archiveFragment.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                    archiveFragment.presenter.getNextIssueMoments(date)
                }
            }
        }
    }

}