package de.taz.app.android.ui.home.page.coverflow

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.home.page.IssueFeedPagingAdapter
import de.taz.app.android.ui.moment.MomentView
import java.util.*

const val MAX_VIEWHOLDER_WIDTH_OF_PARENT = 0.8

class CoverflowAdapter(
    private val fragment: CoverflowFragment,
    @LayoutRes private val itemLayoutRes: Int,
    val dateOnClickListener: ((Date) -> Unit)?
) : IssueFeedPagingAdapter(fragment, itemLayoutRes) {

    override fun dateOnClickListener(issueDate: Date) {
        this.dateOnClickListener(issueDate)
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