package de.taz.app.android.ui.home.page.coverflow

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.home.page.CoverViewActionListener
import de.taz.app.android.ui.home.page.IssueFeedAdapter


class CoverflowAdapter(
    private val fragment: CoverflowFragment,
    @LayoutRes private val itemLayoutRes: Int,
    val feed: Feed,
    glideRequestManager: RequestManager,
    onCoverViewActionListener: CoverViewActionListener
) : IssueFeedAdapter(
    fragment,
    itemLayoutRes,
    feed,
    glideRequestManager,
    onCoverViewActionListener,
    observeDownloads = false
) {
    override val dateFormat: DateFormat = DateFormat.None

    private val viewHolderWidth by lazy { calculateViewHolderWidth() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = super.onCreateViewHolder(parent, viewType)
        return setViewHolderSize(viewHolder)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(setViewHolderSize(holder))
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.layoutParams.height = calculateHeight()
    }

    private fun calculateViewHolderWidth(): Int {
        val isLandscape =
            fragment.resources.displayMetrics.heightPixels < fragment.resources.displayMetrics.widthPixels
        val factor = fragment.resources.getFraction(R.fraction.cover_width_screen_factor, 1, 1)

        return if (isLandscape) {
            // in landscape mode it's height bound
            (fragment.resources.displayMetrics.heightPixels * factor).toInt()
        } else {
            // in portrait mode it's width bound
            (fragment.resources.displayMetrics.widthPixels * factor).toInt()
        }
    }

    private fun calculateHeight(): Int = (viewHolderWidth / feed.momentRatio).toInt()


    /**
     * The size of the cover item is determined by both the constraints of the cover dimension
     * which is determined by the feed and the available screen width or height
     * We'd need to determine upper bounds by height _or_ width, depending on whats exceeded first.
     * In Landscape that's typically height while in portrait it's typically width.
     * One might be tempted to let the CoverView determine its width with a combination
     * of dimension constraints and max width/height constraints, and wrap its content by width - but
     * that's hella buggy in API Level < 24. The solution below is pragmatic and seems to work universally
     * Define a fraction of screen bound you wanna fill with the cover and calculate the item width from it.
     */
    private fun setViewHolderSize(viewHolder: ViewHolder): ViewHolder {
        viewHolder.itemView.layoutParams = ViewGroup.LayoutParams(
            viewHolderWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return viewHolder
    }

}