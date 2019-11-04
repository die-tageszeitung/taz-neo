package de.taz.app.android.ui.archive

import android.widget.AbsListView
import de.taz.app.android.util.Log


class ArchiveOnScrollListener(private val archiveFragment: ArchiveFragment): AbsListView.OnScrollListener {

    private val log by Log

    private var lastRequestedDate = ""

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit

    override fun onScroll(
        view: AbsListView?,
        firstVisibleItem: Int,
        visibleItemCount: Int,
        totalItemCount: Int
    ) {
        if (firstVisibleItem + visibleItemCount > totalItemCount - 1.5*visibleItemCount) {
            val requestDate = archiveFragment.archiveListAdapter.getItem(totalItemCount-1).date
            if (requestDate != lastRequestedDate) {
                log.debug("requested next issue Moments for date $requestDate")
                lastRequestedDate = requestDate
                archiveFragment.presenter.downloadNextIssueMoments(requestDate)
            }
        }
    }

}