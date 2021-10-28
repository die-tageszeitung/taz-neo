package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateFormat
import java.util.*

enum class CoverType {
    ANIMATED, STATIC, FRONT_PAGE
}

data class CoverViewData(
    val issueKey: AbstractIssueKey,
    val downloadStatus: DownloadStatus,
    val momentType: CoverType,
    val momentUri: String?,
    val dimension: String
)

/**
 *  [IssueFeedAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class IssueFeedAdapter(
    private val fragment: IssueFeedFragment,
    @LayoutRes private val itemLayoutRes: Int,
    private val feed: Feed,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener
) : RecyclerView.Adapter<IssueFeedAdapter.ViewHolder>() {

    abstract val dateFormat: DateFormat

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
        getItem(position)?.let {
            holder.bind(fragment, it)
        } ?: throw IllegalStateException("No date on position $position")
    }

    fun getItem(position: Int): Date? {
        return feed.publicationDates.getOrNull(position)
    }

    fun getPosition(date: Date): Int {
        return feed.publicationDates.indexOf(date)
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private var binder: CoverViewBinding? = null

        fun bind(fragment: IssueFeedFragment, date: Date) {
            binder = if (fragment.viewModel.pdfModeLiveData.value == true) {
                FrontpageViewBinding(
                    fragment.requireContext().applicationContext,
                    fragment,
                    IssuePublication(feed.name, simpleDateFormat.format(date)),
                    dateFormat = dateFormat,
                    glideRequestManager = glideRequestManager,
                    onMomentViewActionListener
                )
            } else {
                MomentViewBinding(
                    fragment.requireContext().applicationContext,
                    fragment,
                    IssuePublication(feed.name, simpleDateFormat.format(date)),
                    dateFormat = dateFormat,
                    glideRequestManager = glideRequestManager,
                    onMomentViewActionListener
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
