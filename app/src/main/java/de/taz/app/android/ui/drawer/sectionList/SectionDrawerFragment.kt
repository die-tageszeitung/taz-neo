package de.taz.app.android.ui.drawer.sectionList

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.monkey.observeDistinctUntil
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.*
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_drawer_sections.*
import kotlinx.coroutines.*

const val ACTIVE_POSITION = "active position"

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment(R.layout.fragment_drawer_sections) {
    private val issueContentViewModel: IssueContentViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueContentViewModel::class.java)
    }
    private var recyclerAdapter: SectionListAdapter? = null

    private var issueOperations: IssueOperations? = null

    // these variables exist only for recreation of the fragment
    private var issueDate: String? = null
    private var issueFeed: String? = null
    private var issueStatus: IssueStatus? = null


    private var fontHelper: FontHelper? = null
    private var issueRepository: IssueRepository? = null
    private var momentRepository: MomentRepository? = null

    private var updated: Boolean = false

    var defaultTypeface: Typeface? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fontHelper = FontHelper.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerAdapter = recyclerAdapter ?: SectionListAdapter(this)
        defaultTypeface = ResourcesCompat.getFont(requireContext(), R.font.aktiv_grotesk_bold)

        restore(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_drawer_sections_list.apply {
            setHasFixedSize(true)
            if (layoutManager == null) {
                layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
            }
            if (adapter == null) {
                adapter = recyclerAdapter
            }
        }

        fragment_drawer_sections_moment.setOnClickListener {
            getMainView()?.showHome(skipToIssue = issueOperations)
            getMainView()?.closeDrawer()
        }

        fragment_drawer_sections_imprint.apply {
            setOnClickListener {
                showImprint()
            }
        }

        issueContentViewModel.issueStubAndDisplayableKeyLiveData.observe(this.viewLifecycleOwner) { (issueStub, _) ->
            lifecycleScope.launch(Dispatchers.Main) {
                fragment_drawer_sections_imprint.apply {
                    typeface = if (issueStub.isWeekend) {
                        fontHelper?.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                    } else {
                        defaultTypeface
                    }
                    visibility = View.VISIBLE
                }
            }
        }

        issueContentViewModel.activeSectionTitleLiveData.observe(this.viewLifecycleOwner) {
            if (it != null) {
                setActiveSection(it)
            }
        }

        issueContentViewModel.imprintArticleLiveData.observe(this.viewLifecycleOwner) {
            if (it != null) {
                issueContentViewModel.displayableKeyLiveData.value?.let { displayableKey ->
                    setActiveSection(displayableKey)
                }
            }
        }

    }

    private fun restore(savedInstanceState: Bundle?) {
        savedInstanceState?.apply {
            recyclerAdapter?.activePosition = getInt(ACTIVE_POSITION, RecyclerView.NO_POSITION)
            runIfNotNull(
                getString(ISSUE_FEED),
                getString(ISSUE_DATE),
                getString(ISSUE_STATUS)
            ) { issueFeed, issueDate, issueStatus ->
                this@SectionDrawerFragment.issueDate = issueDate
                this@SectionDrawerFragment.issueFeed = issueFeed
                this@SectionDrawerFragment.issueStatus = IssueStatus.valueOf(issueStatus)

                CoroutineScope(Dispatchers.IO).launch {
                    issueRepository?.getIssueStubByFeedAndDate(
                        issueFeed, issueDate, IssueStatus.valueOf(issueStatus)
                    )?.let {
                        setIssueOperations(it)
                        showIssueStub()
                    }
                }
            }
        }
    }

    fun setIssueOperations(issueOperations: IssueOperations) = activity?.runOnUiThread {
        if (issueOperations != this.issueOperations) {
            updated = true
            view?.alpha = 0f

            this.issueDate = issueOperations.date
            this.issueStatus = issueOperations.status
            this.issueFeed = issueOperations.feedName
            this.issueOperations = issueOperations

            recyclerAdapter?.setIssueOperations(issueOperations)
        }
    }

    fun showIssueStub() = activity?.runOnUiThread {
        if (updated) {
            updated = false

            lifecycleScope.launchWhenResumed {
                setMomentDate()
                showImprint()
                val momentJob = showMoment()
                recyclerAdapter?.show()

                momentJob?.join()
            }
        }
        view?.scrollY = 0
        view?.animate()?.alpha(1f)?.duration = 500
    }

    private fun setActiveSection(activePosition: Int) = activity?.runOnUiThread {
        recyclerAdapter?.activePosition = activePosition
        if (activePosition != RecyclerView.NO_POSITION) {
            fragment_drawer_sections_imprint?.apply {
                setTextColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.drawer_sections_item,
                        null
                    )
                )
            }
        }
    }

    private fun setActiveSection(sectionFileName: String) {
        if (sectionFileName == issueContentViewModel.imprintArticleLiveData.value?.key) {
            setActiveSection(RecyclerView.NO_POSITION)
            setImprintActive()
        } else {
            setImprintInactive()
            recyclerAdapter?.positionOf(sectionFileName)?.let {
                setActiveSection(it)
            }
        }

    }

    private fun setImprintActive() {
        fragment_drawer_sections_imprint.apply {
            setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.drawer_sections_item_highlighted,
                    null
                )
            )
        }
    }

    private fun setImprintInactive() {
        fragment_drawer_sections_imprint.apply {
            setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.drawer_sections_item,
                    null
                )
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(ISSUE_DATE, issueDate)
        outState.putString(ISSUE_FEED, issueFeed)
        outState.putString(ISSUE_STATUS, issueStatus?.toString())
        recyclerAdapter?.activePosition?.let {
            outState.putInt(ACTIVE_POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

    private fun showMoment(): Job? = lifecycleScope.launch(Dispatchers.IO) {
        issueOperations?.let { issueOperations ->
            val moment = momentRepository?.get(issueOperations)
            moment?.apply {
                if (!isDownloaded(context?.applicationContext)) {
                    download(context?.applicationContext)
                }
                lifecycleScope.launchWhenResumed {
                    isDownloadedLiveData(context?.applicationContext).observeDistinctUntil(
                        viewLifecycleOwner,
                        { momentIsDownloadedObservationCallback(it) }, { it }
                    )
                }
            }
        }
    }

    private fun showImprint() {
        issueContentViewModel.imprintArticleLiveData.value?.key?.let {
            issueContentViewModel.setDisplayable(it)
            getMainView()?.closeDrawer()
        }
    }

    private fun momentIsDownloadedObservationCallback(isDownloaded: Boolean?) {
        if (isDownloaded == true) {
            issueOperations?.let { issueOperations ->
                fragment_drawer_sections_moment?.apply {
                    displayIssue(issueOperations)
                    visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setMomentDate() {
        issueOperations?.let { issueOperations ->
            fragment_drawer_sections_date?.text =
                DateHelper.stringToLongLocalizedString(issueOperations.date)
        }
    }

    override fun onDestroyView() {
        fragment_drawer_sections_list.adapter = null
        super.onDestroyView()
    }

}
