package de.taz.app.android.ui.home.page.archive

import androidx.annotation.LayoutRes
import com.bumptech.glide.RequestManager
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.PublicationDate
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.home.page.CoverViewDate
import de.taz.app.android.ui.home.page.HomeMomentViewActionListener
import de.taz.app.android.ui.home.page.IssueFeedAdapter

class ArchiveAdapter(
    fragment: ArchiveFragment,
    @LayoutRes private val itemLayoutRes: Int,
    feed: Feed,
    glideRequestManager: RequestManager
) : IssueFeedAdapter(
    fragment,
    itemLayoutRes,
    feed,
    glideRequestManager,
    HomeMomentViewActionListener(
        fragment
    ),
    observeDownloads = true
) {
    override fun formatDate(publicationDate: PublicationDate): CoverViewDate {
        return when {
            publicationDate.validity != null -> CoverViewDate(
                DateHelper.dateToMediumRangeString(publicationDate.date, publicationDate.validity),
                DateHelper.dateToShortRangeString(publicationDate.date, publicationDate.validity)
            )
            else -> CoverViewDate(
                DateHelper.dateToMediumLocalizedString(publicationDate.date),
                DateHelper.dateToShortLocalizedString(publicationDate.date)
            )
        }
    }
}