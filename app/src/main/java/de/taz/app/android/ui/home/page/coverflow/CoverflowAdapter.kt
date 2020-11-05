package de.taz.app.android.ui.home.page.coverflow

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import de.taz.app.android.api.models.Feed
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.DUMMY_FEED_LIST
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import java.util.*

const val MAX_VIEWHOLDER_WIDTH_OF_PARENT = 0.8

class CoverflowAdapter(
    fragment: CoverflowFragment,
    @LayoutRes private val itemLayoutRes: Int,
    feed: Feed,
    private val onDateClicked: (Date) -> Unit,
) : IssueFeedAdapter(fragment, itemLayoutRes, feed, DUMMY_FEED_LIST.map { simpleDateFormat.parse(it) }) {

    override fun dateOnClickListener(issueDate: Date) {
        onDateClicked.invoke(issueDate)
    }

    override val dateFormat: DateFormat = DateFormat.LongWithWeekDay

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = super.onCreateViewHolder(parent, viewType)

        val layoutParams: ViewGroup.LayoutParams = viewHolder.itemView.layoutParams
        viewHolder.itemView.post {
            if (viewHolder.itemView.width > MAX_VIEWHOLDER_WIDTH_OF_PARENT  * parent.width) {
                layoutParams.width = (parent.width * MAX_VIEWHOLDER_WIDTH_OF_PARENT).toInt()
                viewHolder.itemView.layoutParams = layoutParams
            }
        }

        return viewHolder
    }
}