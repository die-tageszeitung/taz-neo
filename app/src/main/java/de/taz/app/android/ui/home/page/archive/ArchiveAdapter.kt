package de.taz.app.android.ui.home.page.archive

import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.HomePageAdapter
import de.taz.app.android.ui.moment.MomentView
import kotlinx.coroutines.launch


class ArchiveAdapter(
    private val fragment: ArchiveFragment,
    @LayoutRes private val itemLayoutRes: Int
) : HomePageAdapter(fragment, itemLayoutRes) {

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        getItem(position)?.let { issueStub ->
            fragment.getLifecycleOwner().lifecycleScope.launch {
                val momentView = viewHolder.itemView.findViewById<MomentView>(
                        R.id.fragment_cover_flow_item
                )
                momentView.displayIssue(
                    issueStub,
                    dateFormat= DateFormat.LongWithoutWeekDay
                )
            }
        }
    }
}