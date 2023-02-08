package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.FrontpagePublication
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.ui.home.page.IssueFeedAdapter.ViewHolder
import de.taz.app.android.util.getIndexOfDate
import java.util.*

enum class CoverType {
    ANIMATED, STATIC, FRONT_PAGE
}

data class CoverViewData(
    val momentType: CoverType,
    val momentUri: String?,
    val dimension: String
)

class CoverViewDate(
    val dateString: String,
    val dateStringShort: String?
)

/**
 *  [IssueFeedAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class IssueFeedAdapter(
    private val fragment: IssueFeedFragment<*>,
    @LayoutRes private val itemLayoutRes: Int,
    private val feed: Feed,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener,
    private val observeDownloads: Boolean
) : RecyclerView.Adapter<IssueFeedAdapter.ViewHolder>() {

    abstract fun formatDate(publicationDate: PublicationDate): CoverViewDate?

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(fragment.context).inflate(
                itemLayoutRes, parent, false
            )
        )
    }

    override fun getItemCount(): Int {
        return feed.publicationDates.size
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.unbind()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.unbind()

        val item = getItem(position)
        item?.let {
            val coverViewDate = formatDate(item)
            holder.bind(fragment, item.date, coverViewDate)
        }
    }

    fun getItem(position: Int): PublicationDate? {
        return feed.publicationDates.getOrNull(position)
    }

    fun getPosition(date: Date): Int {
        return feed.publicationDates.getIndexOfDate(date)
        // FIXME (johannes): there was a commit by eike to ignore negative results if the date is not found
        //                   it returned 0 instead. we should check if that is a working error fallback
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var binder: CoverViewBinding? = null

        fun bind(fragment: IssueFeedFragment<*>, date: Date, coverViewDate: CoverViewDate?) {

            binder = if (fragment.viewModel.pdfModeLiveData.value == true) {
                FrontpageViewBinding(
                    fragment,
                    FrontpagePublication(feed.name, simpleDateFormat.format(date)),
                    coverViewDate,
                    glideRequestManager,
                    onMomentViewActionListener,
                    observeDownloads
                )
            } else {
                MomentViewBinding(
                    fragment,
                    MomentPublication(feed.name, simpleDateFormat.format(date)),
                    coverViewDate,
                    glideRequestManager,
                    onMomentViewActionListener,
                    observeDownloads,
                )
            }
            binder?.prepareDataAndBind(itemView.findViewById(R.id.fragment_cover_flow_item))

        }

        fun unbind() {
            binder?.unbind()
            binder = null
        }
    }
}
