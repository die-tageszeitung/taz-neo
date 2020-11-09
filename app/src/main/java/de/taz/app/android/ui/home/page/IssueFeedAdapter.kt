package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.Log
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.singletons.DateFormat
import java.lang.IllegalStateException
import java.util.*

enum class MomentType {
    ANIMATED, STATIC
}

data class MomentViewData(
    val issueStub: IssueStub,
    val downloadStatus: DownloadStatus,
    val momentType: MomentType,
    val momentUri: String?,
    val dimension: String
)

/**
 *  [IssueFeedAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class IssueFeedAdapter(
    private val fragment: HomePageFragment,
    @LayoutRes private val itemLayoutRes: Int,
    private val feed: Feed
) : RecyclerView.Adapter<IssueFeedAdapter.ViewHolder>() {
    private val log by Log

    open fun dateOnClickListener(issueDate: Date): Unit = Unit
    abstract val dateFormat: DateFormat

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(fragment.context).inflate(
                itemLayoutRes, parent, false
            ) as ConstraintLayout
        )
    }

    override fun getItemCount(): Int {
        return feed.publicationDates.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.unbind()
        getItem(position)?.let {
            holder.bind(fragment, it)
        } ?: throw IllegalStateException("No date on position $position")
    }

    fun getItem(position: Int): Date? {
        return feed.publicationDates[position]
    }

    fun getPosition(date: Date): Int {
        return feed.publicationDates.indexOf(date)
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder constructor(itemView: ConstraintLayout): RecyclerView.ViewHolder(itemView) {
        private var binder: MomentViewDataBinding? = null

        fun bind(fragment: HomePageFragment, date: Date) {
            binder = MomentViewDataBinding(
                fragment,
                date,
                dateClickedListener = ::dateOnClickListener,
                dateFormat = dateFormat
            ).apply {
                bindView(itemView.findViewById(R.id.fragment_cover_flow_item), feed)
            }
        }

        fun unbind() {
            binder?.unbind()
            binder = null
        }
    }
}
