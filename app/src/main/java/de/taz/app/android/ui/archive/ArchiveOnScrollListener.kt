package de.taz.app.android.ui.archive

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.DateHelper
import de.taz.app.android.util.Log

const val NUMBER_OF_REQUESTED_MOMENTS = 10

/**
 * [ArchiveOnScrollListener] ensures endless scrolling is possible
 * if there aren't enough next items in the GridLayoutManager they will be downloaded
 */
class ArchiveOnScrollListener(
    private val archiveFragment: ArchiveFragment
): RecyclerView.OnScrollListener() {

    private val log by Log

    private var lastRequestedDate = ""

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        val layoutManager = recyclerView.layoutManager as GridLayoutManager
        val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        val visibleItemCount = lastVisibleItem - firstVisibleItem
        val totalItemCount = layoutManager.itemCount

        if (lastVisibleItem > totalItemCount - 2 * visibleItemCount) {
            val requestDate = archiveFragment.archiveListAdapter.getItem(totalItemCount - 1).date

            if (lastRequestedDate == "" || requestDate <= lastRequestedDate) {
                log.debug("requested next issue Moments for date $requestDate")
                lastRequestedDate = DateHelper.stringToStringWithDelta(
                    requestDate, -NUMBER_OF_REQUESTED_MOMENTS
                ) ?: ""

                archiveFragment.presenter.getNextIssueMoments(requestDate, NUMBER_OF_REQUESTED_MOMENTS)
            }
        }
    }

}