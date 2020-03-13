package de.taz.app.android.ui.home.page

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.lang.IndexOutOfBoundsException
import de.taz.app.android.R
import de.taz.app.android.ui.bottomSheet.issue.IssueBottomSheetFragment
import de.taz.app.android.ui.moment.MomentView


/**
 *  [HomePageAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
abstract class HomePageAdapter(
    private val fragment: HomePageContract.View,
    @LayoutRes private val itemLayoutRes: Int,
    private val presenter: HomePageContract.Presenter,
    private val dateOnClickListenerFunction: (() -> Unit)? = null
) : RecyclerView.Adapter<HomePageAdapter.ViewHolder>() {

    private val fileHelper = FileHelper.getInstance()
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

    fun setAuthStatus(authStatus: AuthStatus) {
        if (this.authStatus != authStatus) {
            log.debug("setting authStatus to ${authStatus.name}")
            this.authStatus = authStatus
            filterAndSetIssues()
        }
    }

    open fun setIssueStubs(issues: List<IssueStub>) {
        if (allIssueStubList != issues) {
            log.debug("setting issueStubs to a list of ${issues.size} issues")
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
        // only show regular issue if 2 exist
        // i.e. when user is not logged in anymore but has issues from before
        filteredIssueList.forEach { item ->
            val issuesAtSameDate = filteredIssueList.filter {
                item.date == it.date && item.feedName == it.feedName
            }
            if (issuesAtSameDate.size > 1) {
                mutableFilteredIssueList.removeAll(
                    issuesAtSameDate.filter { it.status != IssueStatus.regular }
                )
            }
        }

        return mutableFilteredIssueList
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
            itemView.setOnClickListener {
                fragment.getLifecycleOwner().lifecycleScope.launch {
                    getItem(adapterPosition)?.let {
                        presenter.onItemSelected(it)
                    }
                }
            }

            itemView.setOnLongClickListener { view ->
                log.debug("onLongClickListener triggered for view: $view!")
                getItem(adapterPosition)?.let { item ->
                    fragment.getMainView()?.let { mainView ->
                        fragment.showBottomSheet(IssueBottomSheetFragment.create(mainView, item))
                    }
                }
                true
            }

            dateOnClickListenerFunction?.let{ dateOnClickListenerFunction ->
                itemView.findViewById<TextView>(R.id.fragment_archive_moment_date).setOnClickListener {
                    dateOnClickListenerFunction()
                }
            }
        }

    }
}
