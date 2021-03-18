package de.taz.app.android.ui.home.page.coverflow

import androidx.annotation.LayoutRes
import com.bumptech.glide.RequestManager
import de.taz.app.android.api.models.Feed
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.CoverViewActionListener
import de.taz.app.android.ui.home.page.IssueFeedAdapter


class CoverflowAdapter(
    fragment: CoverflowFragment,
    @LayoutRes private val itemLayoutRes: Int,
    feed: Feed,
    glideRequestManager: RequestManager,
    onCoverViewActionListener: CoverViewActionListener
) : IssueFeedAdapter(
    fragment,
    itemLayoutRes,
    feed,
    glideRequestManager,
    onCoverViewActionListener
) {
    override val dateFormat: DateFormat = DateFormat.None
}