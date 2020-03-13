package de.taz.app.android.ui.home.page.coverflow

import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.moment.MomentView
import kotlinx.coroutines.launch

class CoverflowAdapter(
    private val fragment: CoverflowContract.View,
    @LayoutRes private val itemLayoutRes: Int,
    presenter: CoverflowContract.Presenter,
    dateOnClickListener: (() -> Unit)?
) : HomePageAdapter(fragment, itemLayoutRes, presenter, dateOnClickListener) {

    override fun setIssueStubs(issues: List<IssueStub>) {
        val skipToLast = visibleIssueStubList.isEmpty()
        super.setIssueStubs(issues)
        if(skipToLast) {
            fragment.skipToEnd()
        }
    }

    override fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        val skipToLast = visibleIssueStubList.isEmpty()
        super.setInactiveFeedNames(inactiveFeedNames)
        if(skipToLast) {
            fragment.skipToEnd()
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        getItem(position)?.let { issueStub ->
            fragment.getLifecycleOwner().lifecycleScope.launch {
                val momentView = viewHolder.itemView.findViewById<MomentView>(R.id.fragment_cover_flow_item)
                momentView.displayIssue(issueStub, dateFormat=DateFormat.LongWithWeekDay)
            }
        }
    }
}