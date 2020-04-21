package de.taz.app.android.ui.drawer.sectionList

import android.os.Bundle
import android.view.Gravity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.ISSUE_DATE
import de.taz.app.android.ui.webview.pager.ISSUE_FEED
import de.taz.app.android.ui.webview.pager.ISSUE_STATUS
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_drawer_sections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment(R.layout.fragment_drawer_sections) {

    private lateinit var recyclerAdapter: SectionListAdapter

    private var issueFeed: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerAdapter = SectionListAdapter(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        fragment_drawer_sections_moment.setOnClickListener {
            getMainView()?.showHome()
            getMainView()?.closeDrawer()
        }

        fragment_drawer_sections_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
            adapter = recyclerAdapter
        }

        restore(savedInstanceState)

    }

    private fun restore(savedInstanceState: Bundle?) {
        savedInstanceState?.apply {
            runIfNotNull(
                getString(ISSUE_FEED),
                getString(ISSUE_DATE),
                getString(ISSUE_STATUS)
            ) { issueFeed, issueDate, issueStatus ->
                // these lines are necessary as setting the values after fetching issueOperations
                // takes too long
                this@SectionDrawerFragment.issueFeed = issueFeed
                this@SectionDrawerFragment.issueDate = issueDate
                this@SectionDrawerFragment.issueStatus = IssueStatus.valueOf(issueStatus)

                CoroutineScope(Dispatchers.IO).launch {
                    val issueOperations =
                        IssueRepository.getInstance(activity?.applicationContext)
                            .getIssueStubByFeedAndDate(
                                issueFeed, issueDate, IssueStatus.valueOf(issueStatus)
                            )
                    activity?.runOnUiThread {
                        issueOperations?.let { setIssueOperations(it) }
                    }
                }
            }
        }
    }

    fun setIssueOperations(issueOperations: IssueOperations) {
        issueDate = issueOperations.date
        issueFeed = issueOperations.feedName
        issueStatus = issueOperations.status
        recyclerAdapter.setIssueOperations(issueOperations)
    }

    fun showIssueStub() {
        recyclerAdapter.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_DATE, issueDate)
        outState.putString(ISSUE_FEED, issueFeed)
        outState.putString(ISSUE_STATUS, issueStatus?.toString())
        super.onSaveInstanceState(outState)
    }

    fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

}
