package de.taz.app.android.ui.drawer.sectionList

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.home.page.MomentViewActionListener
import de.taz.app.android.ui.home.page.MomentViewData
import de.taz.app.android.ui.home.page.MomentViewDataBinding
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.activity_taz_viewer.*
import kotlinx.android.synthetic.main.fragment_drawer_sections.*
import kotlinx.android.synthetic.main.view_moment.*
import kotlinx.coroutines.*

const val ACTIVE_POSITION = "active position"

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment(R.layout.fragment_drawer_sections) {
    private val issueContentViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueViewerViewModel::class.java)
    }

    private val viewModel: SectionDrawerViewModel by activityViewModels()

    private val bookmarkPagerViewModel: BookmarkPagerViewModel by lazy {
        ViewModelProvider(
            this.requireActivity(),
            SavedStateViewModelFactory(this.requireActivity().application, this.requireActivity())
        ).get(BookmarkPagerViewModel::class.java)
    }

    private val log by Log

    private lateinit var sectionListAdapter: SectionListAdapter

    private lateinit var fontHelper: FontHelper
    private lateinit var dataService: DataService
    private lateinit var issueRepository: IssueRepository
    private lateinit var momentRepository: MomentRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var feedRepository: FeedRepository

    private lateinit var fileHelper: FileHelper

    private var defaultTypeface: Typeface? = null
    private var weekendTypeface: Typeface? = null

    private var momentBinder: MomentViewDataBinding? = null

    private lateinit var currentIssueStub: IssueStub

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fontHelper = FontHelper.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        sectionRepository = SectionRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
        dataService = DataService.getInstance(context.applicationContext)
        dataService = DataService.getInstance(context.applicationContext)
        fileHelper = FileHelper.getInstance(context.applicationContext)
        feedRepository = FeedRepository.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionListAdapter =
            SectionListAdapter(::onSectionItemClickListener, requireActivity().theme)
        defaultTypeface = ResourcesCompat.getFont(requireContext(), R.font.appFontBold)
        weekendTypeface =
            runBlocking { fontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME) }
        sectionListAdapter.typeface = defaultTypeface

        restore(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Either the issueContentViewModel can change the content of this drawer ...
        issueContentViewModel.issueKeyAndDisplayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) { issueKeyWithDisplayable ->
            lifecycleScope.launchWhenResumed {
                log.debug("Set issue issueKey from IssueContent")
                if (!::currentIssueStub.isInitialized || issueKeyWithDisplayable?.issueKey != currentIssueStub.issueKey) {
                    showIssue(issueKeyWithDisplayable?.issueKey)
                }
                issueKeyWithDisplayable?.let {
                    maybeSetActiveSection(it.issueKey, it.displayableKey)
                }
            }
        }

        // or the bookmarkpager
        bookmarkPagerViewModel.currentIssueAndArticleLiveData.observeDistinct(this.viewLifecycleOwner) { (issueStub, displayableKey) ->
            lifecycleScope.launchWhenResumed {
                log.debug("Set issue ${issueStub.issueKey} from BookmarkPager")
                if (issueStub.issueKey == bookmarkPagerViewModel.currentIssue?.issueKey) {
                    showIssue(issueStub.issueKey)
                }
                maybeSetActiveSection(issueStub.issueKey, displayableKey)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_drawer_sections_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
            adapter = sectionListAdapter
        }

        fragment_drawer_sections_moment.apply {
            moment_container.setOnClickListener {
                finishAndShowIssue(currentIssueStub.issueKey)
            }
        }

        fragment_drawer_sections_imprint.apply {
            setOnClickListener {
                lifecycleScope.launch {
                    showImprint()
                }
            }
        }
    }

    private suspend fun maybeSetActiveSection(issueKey: IssueKey, displayableKey: String) {
        val imprint = lazy { issueRepository.getImprint(issueKey) }
        val section =
            lazy { sectionRepository.getSectionStubForArticle(displayableKey)?.sectionFileName }
        withContext(Dispatchers.IO) {
            when {
                displayableKey == imprint.value?.key -> {
                    sectionListAdapter.activePosition = RecyclerView.NO_POSITION
                    setImprintActive()
                }
                displayableKey.startsWith("art") -> {
                    setImprintInactive()
                    section.value?.let { setActiveSection(it) }
                }
                displayableKey.startsWith("sec") -> {
                    setImprintInactive()
                    setActiveSection(displayableKey)
                }
                else -> {
                    setImprintInactive()
                    setActiveSection(null)
                }
            }
        }
    }

    private fun onSectionItemClickListener(clickedSection: SectionStub) {
        lifecycleScope.launch {
            issueContentViewModel.setDisplayable(
                currentIssueStub.issueKey,
                clickedSection.key
            )
        }
        viewModel.drawerOpen.postValue(false)
    }

    private fun restore(savedInstanceState: Bundle?) {
        savedInstanceState?.apply {
            sectionListAdapter.activePosition = getInt(ACTIVE_POSITION, RecyclerView.NO_POSITION)
        }
    }

    private suspend fun showIssue(issueKey: IssueKey?) = withContext(Dispatchers.Main) {
        if (issueKey != null) {
            val issueStub = withContext(Dispatchers.IO) { dataService.getIssueStub(issueKey) }
            setMomentDate(issueStub)
            showMoment(issueStub)
            issueStub?.let {
                currentIssueStub = issueStub
                val sections = withContext(Dispatchers.IO) {
                    sectionRepository.getSectionStubsForIssue(issueStub.issueKey)
                }
                log.debug("SectionDrawer sets new sections: $sections")
                sectionListAdapter.sectionList = sections
                sectionListAdapter.typeface =
                    if (issueStub.isWeekend) weekendTypeface else defaultTypeface
                view?.scrollY = 0
                view?.animate()?.alpha(1f)?.duration = 500
                fragment_drawer_sections_imprint.apply {
                    typeface = if (issueStub.isWeekend) weekendTypeface else defaultTypeface
                    val isImprint = withContext(Dispatchers.IO) {
                        issueRepository.getImprint(issueStub.issueKey) != null
                    }
                    visibility = if (isImprint) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
            }
        } else {
            sectionListAdapter.sectionList = emptyList()
        }
    }

    private fun setActiveSection(activePosition: Int) = activity?.runOnUiThread {
        sectionListAdapter.activePosition = activePosition
    }

    private fun setActiveSection(sectionFileName: String?) {
        if (sectionFileName == null) {
            sectionListAdapter.activePosition = RecyclerView.NO_POSITION
        } else {
            sectionListAdapter.positionOf(sectionFileName)?.let {
                setActiveSection(it)
            } ?: run {
                sectionListAdapter.activePosition = RecyclerView.NO_POSITION
            }
        }
    }

    private suspend fun setImprintActive() = withContext(Dispatchers.Main) {
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

    private suspend fun setImprintInactive() = withContext(Dispatchers.Main) {
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
        sectionListAdapter.activePosition.let {
            outState.putInt(ACTIVE_POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    private fun finishAndShowIssue(issueKey: IssueKey) {
        Intent().apply {
            putExtra(MainActivity.KEY_ISSUE_KEY, issueKey)
            requireActivity().setResult(MainActivity.KEY_RESULT_SKIP_TO_ISSUE_KEY, this)
            requireActivity().finish()
        }
    }

    private suspend fun showMoment(issueStub: IssueStub?) = withContext(Dispatchers.IO) {
        val moment = issueStub?.let { momentRepository.get(it) }
        moment?.apply {
            withContext(Dispatchers.Main) {
                momentBinder?.unbind()
            }
            if (!isDownloaded()) {
                dataService.ensureDownloaded(moment)
            }
            val feed = feedRepository.get(issueStub.feedName)
            momentBinder = MomentViewDataBinding(
                this@SectionDrawerFragment,
                IssuePublication(feed!!.name, issueStub.date),
                DateFormat.LongWithoutWeekDay,
                Glide.with(this@SectionDrawerFragment),
                object : MomentViewActionListener {
                    override fun onImageClicked(momentViewData: MomentViewData) {
                        finishAndShowIssue(issueStub.issueKey)
                    }
                }
            )
            withContext(Dispatchers.Main) {
                momentBinder?.bindView(fragment_drawer_sections_moment)
                fragment_drawer_sections_moment.visibility = View.VISIBLE
                fragment_moment_date.visibility = View.GONE
            }
        } ?: run {
            momentBinder?.unbind()
        }
    }


    private fun showImprint() {
        runIfNotNull(
            issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
            issueContentViewModel.imprintArticleLiveData.value?.key
        ) { issueKey, displayKey ->
            lifecycleScope.launch {
                issueContentViewModel.setDisplayable(issueKey, displayKey)
            }
            viewModel.drawerOpen.postValue(false)
        }
    }


    private fun setMomentDate(issueStub: IssueStub?) {
        fragment_drawer_sections_date?.text =
            issueStub?.date?.let(DateHelper::stringToLongLocalizedString) ?: ""
    }

    override fun onDestroyView() {
        fragment_drawer_sections_list.adapter = null
        super.onDestroyView()
    }

}
