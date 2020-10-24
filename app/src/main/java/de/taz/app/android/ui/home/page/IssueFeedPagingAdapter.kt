package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.Log
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import kotlin.collections.ArrayList


/**
 *  [IssueFeedPagingAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class IssueFeedPagingAdapter(
    private val fragment: HomePageFragment,
    @LayoutRes private val itemLayoutRes: Int
) : PagingDataAdapter<IssueStub, IssueFeedPagingAdapter.ViewHolder>(IssueStubComperator) {
    private val activeFeeds: MutableList<Feed> = ArrayList()
    private val issueStubs: List<IssueStub> = emptyList()
    private val log by Log


    object IssueStubComperator : DiffUtil.ItemCallback<IssueStub>() {
        override fun areItemsTheSame(oldItem: IssueStub, newItem: IssueStub): Boolean {
            // Id is unique.
            return oldItem.issueKey == newItem.issueKey
        }

        override fun areContentsTheSame(oldItem: IssueStub, newItem: IssueStub): Boolean {
            return oldItem == newItem
        }
    }

    fun getPosition(issueKey: IssueKey): Int {
        return issueStubs.indexOfFirst {
            it.issueKey == issueKey
        }
    }

    fun getItemAtPosition(position: Int): IssueStub? {
        return super.getItem(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(fragment.context).inflate(
                itemLayoutRes, parent, false
            ) as ConstraintLayout
        )
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder constructor(itemView: ConstraintLayout) :
        RecyclerView.ViewHolder(itemView) {
        init {
            itemView.findViewById<ImageView>(R.id.fragment_moment_image)?.apply {
                setOnClickListener {
                    fragment.viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                        getItem(absoluteAdapterPosition)?.let {
                            fragment.onItemSelected(it)
                        }
                    }
                }

                setOnLongClickListener { view ->
                    log.debug("onLongClickListener triggered for view: $view!")
                    getItem(absoluteAdapterPosition)?.let { item ->
                        fragment.getMainView()?.let { mainView ->
                            fragment.showBottomSheet(
                                IssueBottomSheetFragment.create(
                                    mainView,
                                    item
                                )
                            )
                        }
                    }
                    true
                }
            }
        }
    }
}
