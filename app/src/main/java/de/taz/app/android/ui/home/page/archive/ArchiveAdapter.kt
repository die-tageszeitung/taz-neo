package de.taz.app.android.ui.home.page.archive

import androidx.annotation.LayoutRes
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.IssueFeedPagingAdapter

class ArchiveAdapter(
    private val fragment: ArchiveFragment,
    @LayoutRes private val itemLayoutRes: Int
) : IssueFeedPagingAdapter(fragment, itemLayoutRes) {
    override val dateFormat: DateFormat = DateFormat.LongWithoutWeekDay
}