package de.taz.app.android.ui.home.page

import android.app.DatePickerDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.lang.IndexOutOfBoundsException
import androidx.core.content.FileProvider.getUriForFile
import de.taz.app.android.R
import java.util.*


/**
 *  [HomePageAdapter] binds the [IssueStub]s to the [RecyclerView]/[ViewPager2]
 *  [ViewHolder] is used to recycle views
 */
open class HomePageAdapter(
    private val fragment: HomePageContract.View,
    @LayoutRes private val itemLayoutRes: Int,
    private val presenter: HomePageContract.Presenter
) : RecyclerView.Adapter<HomePageAdapter.ViewHolder>() {

    private val fileHelper = FileHelper.getInstance()
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


    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        getItem(position)?.let { issueStub ->
            fragment.getLifecycleOwner().lifecycleScope.launch {
                val momentView = viewHolder.itemView.findViewById<MomentView>(R.id.fragment_cover_flow_item)
                momentView.presenter.setIssue(issueStub, feedMap[issueStub.feedName])
            }
        }
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
                fragment.getLifecycleOwner().lifecycleScope.launch {
                    getItem(adapterPosition)?.getIssue()?.moment?.getAllFiles()?.last()?.let{ image ->
                        val imageAsFile = fileHelper.getFile(image)
                        val applicationId = view.context.packageName
                        val imageUriNew = getUriForFile(view.context, "${applicationId}.contentProvider", imageAsFile)

                        log.debug("imageUriNew: $imageUriNew")
                        log.debug("imageAsFile: $imageAsFile")
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, imageUriNew)
                            type = "image/jpg"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        view.context.startActivity(shareIntent)
                    }
                }

                true
            }

            itemView.findViewById<TextView>(R.id.fragment_archive_moment_date).setOnClickListener {view ->
                log.debug("click datepicker")
                val c = Calendar.getInstance()
                val year = c.get(Calendar.YEAR)
                val month = c.get(Calendar.MONTH)
                val day = c.get(Calendar.DAY_OF_MONTH)

                val dpd = DatePickerDialog(view.context, DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
                    // Display Selected date in Toast
                    Toast.makeText(view.context, """$dayOfMonth - ${monthOfYear + 1} - $year""", Toast.LENGTH_LONG).show()

                }, year, month, day)
                dpd.show()
            }
        }
    }
}
