package de.taz.app.android.ui.home.page.archive

import androidx.annotation.LayoutRes
import com.bumptech.glide.RequestManager
import de.taz.app.android.api.models.Feed
import de.taz.app.android.data.DataService
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.HomeMomentViewActionListener
import de.taz.app.android.ui.home.page.IssueFeedAdapter

class ArchiveAdapter(
    fragment: ArchiveFragment,
    @LayoutRes private val itemLayoutRes: Int,
    feed: Feed,
    glideRequestManager: RequestManager,
    showPdfAsMoment: Boolean = false
) : IssueFeedAdapter(
    fragment,
    itemLayoutRes,
    feed,
    glideRequestManager,
    HomeMomentViewActionListener(
        fragment,
        DataService.getInstance(fragment.requireContext().applicationContext)
    ),
    showPdfAsMoment
) {
    override val dateFormat: DateFormat = DateFormat.LongWithoutWeekDay
}