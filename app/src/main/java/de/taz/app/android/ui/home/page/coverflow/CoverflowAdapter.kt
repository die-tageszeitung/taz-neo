package de.taz.app.android.ui.home.page.coverflow

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.moment.MomentView
import kotlinx.coroutines.launch
import java.util.*

const val MAX_VIEWHOLDER_WIDTH_OF_PARENT = 0.8

class CoverflowAdapter(
    private val fragment: CoverflowFragment,
    @LayoutRes private val itemLayoutRes: Int,
    dateOnClickListener: ((Date) -> Unit)?
) : HomePageAdapter(fragment, itemLayoutRes, dateOnClickListener) {

    override fun setIssueStubs(issues: List<IssueStub>) {
        val skipToLast = visibleIssueStubList.isEmpty()
        super.setIssueStubs(issues)
        if (skipToLast) {
            if (fragment.hasSetItem()) {
                fragment.skipToCurrentItem()
            } else {
                fragment.skipToEnd()
            }
        }
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        val skipToLast = visibleIssueStubList.isEmpty()
        super.setInactiveFeedNames(inactiveFeedNames)
        if (skipToLast) {
            fragment.skipToEnd()
        }
    }

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

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        getItem(position)?.let { issueStub ->
            fragment.getLifecycleOwner().lifecycleScope.launchWhenResumed {
                val momentView =
                    viewHolder.itemView.findViewById<MomentView>(R.id.fragment_cover_flow_item)
                momentView.displayIssue(issueStub, dateFormat = DateFormat.LongWithWeekDay)
            }
        }
    }
}