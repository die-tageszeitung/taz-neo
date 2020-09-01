package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.util.Log
import java.lang.IndexOutOfBoundsException
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import de.taz.app.android.ui.moment.MomentView
import java.util.*


/**
 *  [HomePageAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class HomePageAdapter(
    private val fragment: HomePageFragment,
    @LayoutRes private val itemLayoutRes: Int,
    private val dateOnClickListenerFunction: ((Date) -> Unit)? = null
) : RecyclerView.Adapter<HomePageAdapter.ViewHolder>() {

    private var allIssueStubList: List<IssueStub> = emptyList()
    protected var visibleIssueStubList: List<IssueStub> = emptyList()

    private var authStatus = AuthStatus.notValid
    private var feedList: List<Feed> = emptyList()
    private var inactiveFeedNames: Set<String> = emptySet()

    private val log by Log

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

    fun getPosition(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Int {
        return visibleIssueStubList.indexOfFirst {
            it.date == issueDate
                    && it.status == issueStatus
                    && it.feedName == issueFeedName
        }
    }

    fun setAuthStatus(authStatus: AuthStatus) {
        if (this.authStatus != authStatus) {
            this.authStatus = authStatus
            filterAndSetIssues()
        }
    }

    open fun setIssueStubs(issues: List<IssueStub>) {
        if (allIssueStubList != issues) {
            allIssueStubList = issues
            filterAndSetIssues()
        }
    }

    private fun filterIssueStubs(): List<IssueStub> {
        val authenticated = authStatus == AuthStatus.valid

        // do not show public issues if logged in
        val filteredIssueList = allIssueStubList.filter {
            it.feedName !in inactiveFeedNames && (!authenticated || it.status != IssueStatus.public)
        }

        val mutableFilteredIssueList = filteredIssueList.toMutableList()

        // show regular issue if user is logged out only if downloaded else demo issue else public
        filteredIssueList.forEach { item ->
            if (!(item.status == IssueStatus.regular && item.downloadedStatus in listOf(
                    DownloadStatus.done,
                    DownloadStatus.started
                )
                        )
            ) {
                val issuesAtSameDate = filteredIssueList.filter {
                    item.date == it.date && item.feedName == it.feedName
                }
                if (issuesAtSameDate.size > 1) {
                    issuesAtSameDate.firstOrNull {
                        it.status == IssueStatus.regular && it.downloadedStatus in listOf(
                            DownloadStatus.done,
                            DownloadStatus.started
                        )
                    }?.let { mutableFilteredIssueList.remove(item) }
                        ?: issuesAtSameDate.firstOrNull { it.status == IssueStatus.demo }?.let {
                            mutableFilteredIssueList.remove(item)
                        } ?: issuesAtSameDate.firstOrNull { it.status == IssueStatus.public }?.let {
                            if(item.status == IssueStatus.regular && !authenticated) {
                                mutableFilteredIssueList.remove(item)
                            }
                        }
                }
            }
        }
        return mutableFilteredIssueList
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
        fragment.callbackWhenIssueIsSet()
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
            LayoutInflater.from(fragment.context).inflate(
                itemLayoutRes, parent, false
            ) as ConstraintLayout
        )
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.itemView.findViewById<MomentView>(R.id.fragment_cover_flow_item).clear()
        super.onViewRecycled(holder)
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
                        getItem(adapterPosition)?.let {
                            fragment.onItemSelected(it)
                        }
                    }
                }

                setOnLongClickListener { view ->
                    log.debug("onLongClickListener triggered for view: $view!")
                    getItem(adapterPosition)?.let { item ->
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
            dateOnClickListenerFunction?.let { dateOnClickListenerFunction ->
                itemView.findViewById<TextView>(R.id.fragment_moment_date).setOnClickListener {
                    getItem(adapterPosition)?.let { issueStub ->
                        val issueDate = DateHelper.stringToDate(issueStub.date)
                        issueDate?.let {
                            dateOnClickListenerFunction(issueDate)
                        }
                    }
                }
            }
        }
    }
}
