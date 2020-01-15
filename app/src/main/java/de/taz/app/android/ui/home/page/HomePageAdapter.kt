package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.lang.IndexOutOfBoundsException

/**
 *  [HomePageAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
open class HomePageAdapter(
    private val fragment: HomePageContract.View,
    @LayoutRes private val itemLayoutRes: Int,
    private val presenter: HomePageContract.Presenter
) : RecyclerView.Adapter<HomePageAdapter.ViewHolder>() {

    private var allIssueStubList: List<IssueStub> = emptyList()
    protected var visibleIssueStubList: List<IssueStub> = emptyList()

    private var authStatus = AuthStatus.notValid
    private var feedList: List<Feed> = emptyList()
    private var inactiveFeedNames: Set<String> = emptySet()

    private val log by Log

    private val feedMap
        get() = feedList.associateBy { it.name }

    fun getItem(position: Int): IssueStub? {
        return try {
            visibleIssueStubList[position]
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
    }

    override fun getItemCount(): Int {
        return visibleIssueStubList.size
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    fun setAuthStatus(authStatus: AuthStatus) {
        if (this.authStatus != authStatus) {
            log.debug("setting authStatus to ${authStatus.name}")
            this.authStatus = authStatus
            filterAndSetIssues()
        }
    }

    open fun setIssueStubs(issues: List<IssueStub>) {
        if (allIssueStubList != issues) {
            log.debug("settings issueStubs to a list of ${issues.size} issues")
            allIssueStubList = issues
            filterAndSetIssues()
        }
    }

    private fun filterIssueStubs(): List<IssueStub> {
        val authenticated = authStatus == AuthStatus.valid
        return allIssueStubList.filter {
            it.feedName !in inactiveFeedNames && (!authenticated || it.status != IssueStatus.public)
        }
    }

    private fun filterAndSetIssues() {
        val filteredIssueStubs = filterIssueStubs()
        log.debug("after filtering ${filteredIssueStubs.size} remain")
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

    open fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        if (this.inactiveFeedNames != inactiveFeedNames) {
            log.debug("settings inactive feeds to a list of ${inactiveFeedNames.size} feeds")
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
        getItem(position)?.let { issueStub ->
            fragment.getLifecycleOwner().lifecycleScope.launch {
                viewHolder.itemView as MomentView
                viewHolder.itemView.presenter.setIssue(issueStub, feedMap[issueStub.feedName])
            }
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
                    getItem(adapterPosition)?.let {
                        presenter.onItemSelected(it)
                    }
                }
            }
        }
    }
}
