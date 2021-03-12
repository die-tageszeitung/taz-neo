package de.taz.app.android.ui.home.page.coverflow

import androidx.annotation.LayoutRes
import com.bumptech.glide.RequestManager
import de.taz.app.android.api.models.Feed
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.home.page.MomentViewActionListener


class CoverflowAdapter(
    fragment: CoverflowFragment,
    @LayoutRes private val itemLayoutRes: Int,
    feed: Feed,
    glideRequestManager: RequestManager,
    onMomentViewActionListener: MomentViewActionListener
) : IssueFeedAdapter(
    fragment,
    itemLayoutRes,
    feed,
    glideRequestManager,
    onMomentViewActionListener
) {
    override val dateFormat: DateFormat = DateFormat.None
}