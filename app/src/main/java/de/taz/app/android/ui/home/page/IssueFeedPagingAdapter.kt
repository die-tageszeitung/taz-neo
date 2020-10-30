package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.Log
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.singletons.DateFormat
import java.util.*

data class IssueStubViewData(
    val issueStub: IssueStub,
    val downloadStatus: DownloadStatus,
    val momentImageUri: String?,
    val dimension: String
)

/**
 *  [IssueFeedPagingAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class IssueFeedPagingAdapter(
    private val fragment: HomePageFragment,
    @LayoutRes private val itemLayoutRes: Int
) : PagingDataAdapter<IssueStubViewData, IssueFeedPagingAdapter.ViewHolder>(IssueStubComperator) {
    private val log by Log

    open fun dateOnClickListener(issueDate: Date): Unit = Unit
    abstract val dateFormat: DateFormat

    var onViewHolderCreatedListener: ((ViewHolder) -> Unit)? = null
    var onViewHolderBoundListener: ((ViewHolder) -> Unit)? = null

    object IssueStubComperator : DiffUtil.ItemCallback<IssueStubViewData>() {
        override fun areItemsTheSame(oldItem: IssueStubViewData, newItem: IssueStubViewData): Boolean {
            return oldItem.issueStub.issueKey == newItem.issueStub.issueKey
        }

        override fun areContentsTheSame(oldItem: IssueStubViewData, newItem: IssueStubViewData): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(fragment.context).inflate(
                itemLayoutRes, parent, false
            ) as ConstraintLayout
        ).also {
            onViewHolderCreatedListener?.invoke(it)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(fragment, it)
        } ?: holder.unbind()
        onViewHolderBoundListener?.invoke(holder)
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder constructor(itemView: ConstraintLayout): RecyclerView.ViewHolder(itemView) {
        private var binder: MomentViewDataBinding? = null

        fun bind(fragment: HomePageFragment, issueStubViewData: IssueStubViewData) {
            binder = MomentViewDataBinding(
                fragment,
                issueStubViewData,
                dateClickedListener = ::dateOnClickListener,
                dateFormat = dateFormat
            ).apply {
                bindView(itemView.findViewById(R.id.fragment_cover_flow_item))
            }
        }

        fun unbind() {
            binder?.unbind()
            binder = null
        }
    }
}
