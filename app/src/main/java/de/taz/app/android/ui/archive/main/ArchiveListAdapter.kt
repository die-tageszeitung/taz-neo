package de.taz.app.android.ui.archive.main

import android.view.ViewGroup
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.archive.item.ArchiveItemView
import kotlinx.coroutines.*

/**
 *  [ArchiveListAdapter] binds the [IssueStub]s from [ArchiveDataController] to the [RecyclerView]
 *  [ViewHolder] is used to recycle views
 */
class ArchiveListAdapter(
    private val archiveFragment: ArchiveFragment
) : RecyclerView.Adapter<ArchiveListAdapter.ViewHolder>() {

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
            ArchiveIssueStubDiffCallback(visibleIssueStubList, filteredIssueStubs)
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
        val archiveItemView = ArchiveItemView(
            archiveFragment.requireContext()
        )
        val padding = archiveFragment.requireContext().resources.getDimension(
            R.dimen.fragment_archive_item_padding
        )

        archiveItemView.setPadding(padding.toInt())
        return ViewHolder(archiveItemView)
    }


    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val issueStub = getItem(position)
        archiveFragment.lifecycleScope.launch {
            viewHolder.itemView as ArchiveItemView
            viewHolder.itemView.presenter.setIssue(issueStub, feedMap[issueStub.feedName])
        }
    }

    /**
     * ViewHolder for this Adapter
     */
    inner class ViewHolder constructor(itemView: ArchiveItemView) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                archiveFragment.lifecycleScope.launch {
                    archiveFragment.presenter.onItemSelected(getItem(adapterPosition))
                }
            }
        }
    }
}
