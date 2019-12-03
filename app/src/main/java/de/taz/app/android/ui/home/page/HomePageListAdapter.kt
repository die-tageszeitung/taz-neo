package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.moment.MomentView
import kotlinx.coroutines.launch

/**
 *  [HomePageListAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
class HomePageListAdapter(
    private val fragment: HomePageContract.View,
    @LayoutRes private val itemLayoutRes: Int,
    private val presenter: HomePageContract.Presenter
) : RecyclerView.Adapter<HomePageListAdapter.ViewHolder>() {

    private var allIssueStubList: List<IssueStub> = emptyList()
    private var visibleIssueStubList: List<IssueStub> = emptyList()

    private var feedList: List<Feed> = emptyList()
    private var inactiveFeedNames: Set<String> = emptySet()

    private val feedMap
        get() = feedList.associateBy { it.name }

    fun getItem(position: Int): IssueStub {
        return visibleIssueStubList[position]
    }

    override fun getItemCount(): Int {
        return visibleIssueStubList.size
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    fun getItemPosition(issueStub: IssueStub): Int {
        return visibleIssueStubList.indexOf(issueStub)
    }

    fun setIssueStubs(issues: List<IssueStub>) {
        if (allIssueStubList != issues) {
            allIssueStubList = issues
            filterAndSetIssues()
        }
    }

    private fun filterIssueStubs(): List<IssueStub> {
        return allIssueStubList.filter { it.feedName !in inactiveFeedNames }
    }

    private fun filterAndSetIssues() {
        val filteredIssueStubs = filterIssueStubs()
        val diffResult = DiffUtil.calculateDiff(
            HomePageListDiffCallback(
                visibleIssueStubList,
                filteredIssueStubs
            )
        )
        visibleIssueStubList = filteredIssueStubs
        diffResult.dispatchUpdatesTo(this)
    }

    fun setFeeds(feeds: List<Feed>) {
        feedList = feeds
    }

    fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        if (this.inactiveFeedNames != inactiveFeedNames) {
            this.inactiveFeedNames = inactiveFeedNames
            filterAndSetIssues()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(fragment.getContext()).inflate(
                itemLayoutRes, parent, false
            ) as MomentView
        )
    }


    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val issueStub = getItem(position)
        fragment.getLifecycleOwner().lifecycleScope.launch {
            viewHolder.itemView as MomentView
            viewHolder.itemView.presenter.setIssue(issueStub, feedMap[issueStub.feedName])
        }
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder constructor(itemView: MomentView) :
        RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                fragment.getLifecycleOwner().lifecycleScope.launch {
                    presenter.onItemSelected(getItem(adapterPosition))
                }
            }
        }
    }
}
