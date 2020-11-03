package de.taz.app.android.ui.home.page.archive

import androidx.annotation.LayoutRes
import de.taz.app.android.api.models.Feed
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.DUMMY_FEED_LIST
import de.taz.app.android.ui.home.page.IssueFeedAdapter

class ArchiveAdapter(
    fragment: ArchiveFragment,
    @LayoutRes private val itemLayoutRes: Int,
    feed: Feed
) : IssueFeedAdapter(fragment, itemLayoutRes, feed, DUMMY_FEED_LIST.map { simpleDateFormat.parse(it) }) {
    override val dateFormat: DateFormat = DateFormat.LongWithoutWeekDay
}