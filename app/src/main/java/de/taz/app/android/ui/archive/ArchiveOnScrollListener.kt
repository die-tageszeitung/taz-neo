package de.taz.app.android.ui.archive

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.DateHelper
import de.taz.app.android.util.Log

const val NUMBER_OF_REQUESTED_MOMENTS = 10

class ArchiveOnScrollListener(private val archiveFragment: ArchiveFragment): RecyclerView.OnScrollListener() {

    private val log by Log

    private var lastRequestedDate = ""

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        (recyclerView.layoutManager as? GridLayoutManager)?.let { layoutManager ->
            val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
            val visibleItemCount = layoutManager.findLastVisibleItemPosition() - firstVisibleItem
            val totalItemCount = layoutManager.itemCount

            if (firstVisibleItem + visibleItemCount > totalItemCount - 1.5 * visibleItemCount) {
                val requestDate =
                    archiveFragment.archiveListAdapter.getItem(totalItemCount - 1).date

                if (lastRequestedDate == "" || requestDate <= lastRequestedDate) {
                    log.debug("requested next issue Moments for date $requestDate")
                    lastRequestedDate = DateHelper.stringToStringWithDelta(requestDate, -NUMBER_OF_REQUESTED_MOMENTS) ?: ""

                    archiveFragment.presenter.getNextIssueMoments(requestDate, NUMBER_OF_REQUESTED_MOMENTS)
                }
            }
        }
    }

}