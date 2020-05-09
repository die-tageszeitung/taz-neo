package de.taz.app.android.ui.drawer.sectionList

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.Moment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.ISSUE_DATE
import de.taz.app.android.ui.webview.pager.ISSUE_FEED
import de.taz.app.android.ui.webview.pager.ISSUE_STATUS
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_drawer_sections.*
import kotlinx.coroutines.*
import java.util.*

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment(R.layout.fragment_drawer_sections) {

    private lateinit var recyclerAdapter: SectionListAdapter

    private var issueOperations: IssueOperations? = null

    private val dateHelper = DateHelper.getInstance()
    private val issueRepository = IssueRepository.getInstance()
    private val momentRepository = MomentRepository.getInstance()

    private var moment: Moment? = null
    private val momentDownloadedObserver = MomentDownloadedObserver()

    private var updated: Boolean = false

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

    fun setIssueOperations(issueOperations: IssueOperations) = activity?.runOnUiThread {
        if (issueOperations != this.issueOperations) {
            updated = true
            view?.alpha = 0f

            this.issueOperations = issueOperations

            recyclerAdapter.setIssueOperations(issueOperations)
        }
    }

    fun showIssueStub() {
        if (updated) {
            updated = false

            lifecycleScope.launch {
                setMomentDate()
                val imprintJob = showImprint()
                val momentJob = showMoment()
                recyclerAdapter.show()

                imprintJob?.join()
                momentJob?.join()
            }
        }
        view?.scrollY = 0
        view?.animate()?.alpha(1f)?.duration = 500
    }

    fun setActiveSection(activePosition: Int) = activity?.runOnUiThread {
        recyclerAdapter.activePosition = activePosition
    }

    fun setActiveSection(sectionFileName: String) {
        val position = recyclerAdapter.positionOf(sectionFileName)
        setActiveSection(position)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_DATE, issueOperations?.date)
        outState.putString(ISSUE_FEED, issueOperations?.feedName)
        outState.putString(ISSUE_STATUS, issueOperations?.status?.toString())
        super.onSaveInstanceState(outState)
    }

    fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

    private fun showMoment(): Job? = lifecycleScope.launch(Dispatchers.IO) {
        issueOperations?.let { issueOperations ->
            moment = momentRepository.get(issueOperations)
            moment?.apply {
                if (!isDownloaded()) {
                    download(context?.applicationContext)
                }
                withContext(Dispatchers.Main) {
                    isDownloadedLiveData().observe(
                        viewLifecycleOwner,
                        momentDownloadedObserver
                    )
                }
            }
        }
    }

    private fun showImprint(): Job? = lifecycleScope.launch(Dispatchers.IO) {
        issueOperations?.let { issueOperations ->
            val imprint = issueRepository.getImprintStub(issueOperations)
            imprint?.let { showImprint(it) }
        }
    }

    private suspend fun showImprint(imprint: ArticleOperations) = withContext(Dispatchers.Main) {
        fragment_drawer_sections_imprint?.apply {
            text = text.toString().toLowerCase(Locale.getDefault())
            setOnClickListener {
                getMainView()?.apply {
                    showInWebView(imprint.key)
                    closeDrawer()
                }
            }
            typeface = if (issueOperations?.isWeekend == true) {
                FontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
            } else Typeface.create("aktiv_grotesk_bold", Typeface.BOLD)
            visibility = View.VISIBLE
        }
    }


    inner class MomentDownloadedObserver : androidx.lifecycle.Observer<Boolean> {
        override fun onChanged(isDownloaded: Boolean?) {
            if (isDownloaded == true) {
                issueOperations?.let { issueOperations ->
                    moment?.isDownloadedLiveData()?.removeObserver(this)
                    fragment_drawer_sections_moment?.apply {
                        displayIssue(issueOperations)
                        visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setMomentDate() {
        issueOperations?.let { issueOperations ->
            fragment_drawer_sections_date?.text =
                dateHelper.stringToLongLocalizedString(issueOperations.date)
        }
    }

}
