package de.taz.app.android.ui.drawer.sectionList

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.*
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.FragmentDrawerSectionsBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.home.page.CoverViewActionListener
import de.taz.app.android.ui.home.page.MomentViewBinding
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.*

const val ACTIVE_POSITION = "active position"

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : ViewBindingFragment<FragmentDrawerSectionsBinding>() {
    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val viewModel: SectionDrawerViewModel by activityViewModels()
    private val bookmarkPagerViewModel: BookmarkPagerViewModel by activityViewModels()

    private val log by Log

    private lateinit var sectionListAdapter: SectionListAdapter

    private lateinit var fontHelper: FontHelper
    private lateinit var issueRepository: IssueRepository
    private lateinit var contentService: ContentService
    private lateinit var momentRepository: MomentRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var fileEntryRepository: FileEntryRepository

    private lateinit var storageService: StorageService

    private var defaultTypeface: Typeface? = null
    private var weekendTypeface: Typeface? = null

    private var momentBinder: MomentViewBinding? = null

    private lateinit var currentIssueStub: IssueStub

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contentService = ContentService.getInstance(context.applicationContext)
        fontHelper = FontHelper.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        sectionRepository = SectionRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        feedRepository = FeedRepository.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionListAdapter =
            SectionListAdapter(::onSectionItemClickListener, requireActivity().theme)
        lifecycleScope.launch(Dispatchers.IO) {
            val weekendTypefaceFileEntry =
                fileEntryRepository.get(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
            defaultTypeface = ResourcesCompat.getFont(requireContext(), R.font.appFontBold)
            weekendTypeface =
                weekendTypefaceFileEntry?.let {
                    storageService.getFile(it)?.let { file -> fontHelper.getTypeFace(file) }
                }
            withContext(Dispatchers.Main) {
                sectionListAdapter.typeface = defaultTypeface
            }
        }
        restore(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // Either the issueContentViewModel can change the content of this drawer ...
        issueContentViewModel.issueKeyAndDisplayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) { issueKeyWithDisplayable ->
            issueKeyWithDisplayable?.let {
                lifecycleScope.launchWhenResumed {
                    log.debug("Set issue issueKey from IssueContent")
                    if (!::currentIssueStub.isInitialized || it.issueKey != currentIssueStub.issueKey) {
                        showIssue(it.issueKey)
                    }
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

        viewBinding.fragmentDrawerSectionsList.apply {
            setHasFixedSize(true)
            adapter = sectionListAdapter
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
        }

        viewBinding.fragmentDrawerSectionsImprint.setOnClickListener {
            lifecycleScope.launch {
                showImprint()
            }
        }
    }

    private suspend fun maybeSetActiveSection(issueKey: IssueKey, displayableKey: String) {
        val imprint = lifecycleScope.async { issueRepository.getImprint(issueKey) }
        val section = lifecycleScope.async {
            sectionRepository.getSectionStubForArticle(displayableKey)?.sectionFileName
        }
        when {
            displayableKey == imprint.await()?.key -> {
                sectionListAdapter.activePosition = RecyclerView.NO_POSITION
                setImprintActive()
            }
            displayableKey.startsWith("art") -> {
                setImprintInactive()
                section.await()?.let { setActiveSection(it) }
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

    private suspend fun showIssue(issueKey: IssueKey) = withContext(Dispatchers.Main) {
        try {
            val issueStub =
                contentService.downloadMetadata(
                    IssuePublication(issueKey)
                ) as Issue
            currentIssueStub = IssueStub(issueStub)
            viewBinding.fragmentDrawerSectionsMoment.setOnClickListener {
                finishAndShowIssue(IssuePublication(currentIssueStub.issueKey))
            }

            setMomentDate(currentIssueStub)
            showMoment(MomentPublication(currentIssueStub.feedName, currentIssueStub.date))

            val sections = sectionRepository.getSectionStubsForIssue(issueStub.issueKey)

            log.debug("SectionDrawer sets new sections: $sections")
            sectionListAdapter.sectionList = sections

            if (issueStub.isWeekend) {
                sectionListAdapter.typeface = weekendTypeface
            } else {
                sectionListAdapter.typeface = defaultTypeface
            }
            view?.scrollY = 0
            view?.animate()?.alpha(1f)?.duration = 500
            viewBinding.fragmentDrawerSectionsImprint.apply {
                typeface = if (issueStub.isWeekend) weekendTypeface else defaultTypeface
                val isImprint = issueRepository.getImprint(issueStub.issueKey) != null
                if (isImprint) {
                    visibility = View.VISIBLE
                    viewBinding.separatorLineImprintTop.visibility = View.VISIBLE
                    viewBinding.separatorLineImprintBottom.visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                    viewBinding.separatorLineImprintTop.visibility = View.GONE
                    viewBinding.separatorLineImprintBottom.visibility = View.GONE
                }
            }
        } catch (e: ConnectivityException.Recoverable) {
            // do nothing we can not load the issueStub as not in database yet.
            // TODO wait for internet and show it once internet is available
        }
    }

    private fun setActiveSection(activePosition: Int) = activity?.runOnUiThread {
        sectionListAdapter.activePosition = activePosition
    }

    private fun setActiveSection(sectionFileName: String?) {
        if (sectionFileName == null) {
            sectionListAdapter.activePosition = RecyclerView.NO_POSITION
        } else {
            setActiveSection(sectionListAdapter.positionOf(sectionFileName)) ?: run {
                sectionListAdapter.activePosition = RecyclerView.NO_POSITION
            }
        }
    }

    private suspend fun setImprintActive() = withContext(Dispatchers.Main) {
        viewBinding.fragmentDrawerSectionsImprint.setTextColor(
            ResourcesCompat.getColor(
                resources,
                R.color.drawer_sections_item_highlighted,
                null
            )
        )
    }

    private suspend fun setImprintInactive() = withContext(Dispatchers.Main) {
        viewBinding.fragmentDrawerSectionsImprint.setTextColor(
            ResourcesCompat.getColor(
                resources,
                R.color.drawer_sections_item,
                null
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        sectionListAdapter.activePosition.let {
            outState.putInt(ACTIVE_POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    private fun finishAndShowIssue(issuePublication: IssuePublication) =
        MainActivity.start(requireActivity(), issuePublication = issuePublication)

    private suspend fun showMoment(momentPublication: MomentPublication?) =
        withContext(Dispatchers.Main) {
            if (momentPublication == null) {
                momentBinder?.unbind()
                return@withContext
            }
            val moment = try {
                contentService.downloadMetadata(momentPublication) as Moment
            } catch (e: CacheOperationFailedException) {
                val hint = "Cache miss and failed download for moment $momentPublication"
                log.error(hint)
                Sentry.captureException(e, hint)
                return@withContext
            }
            try {
                contentService.downloadToCache(moment)
                momentBinder = MomentViewBinding(
                    this@SectionDrawerFragment,
                    momentPublication,
                    DateFormat.None,
                    Glide.with(this@SectionDrawerFragment),
                    object : CoverViewActionListener {
                        override fun onImageClicked(coverPublication: AbstractCoverPublication) {
                            finishAndShowIssue(
                                IssuePublication(coverPublication)
                            )
                        }
                    },
                    observeDownload = false
                )
                momentBinder?.prepareDataAndBind(viewBinding.fragmentDrawerSectionsMoment)

            } catch (e: CacheOperationFailedException) {
                requireActivity().showIssueDownloadFailedDialog(moment.issueKey)
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
        viewBinding.fragmentDrawerSectionsDate.text =
            if (issueStub?.isWeekend == true
                && !issueStub.validityDate.isNullOrBlank()
            ) {
                val formattedDate = DateHelper.stringToDate(issueStub.date)
                DateHelper.dateToWeekNotation(formattedDate, issueStub.validityDate)
            } else {
                issueStub?.date?.let(DateHelper::stringToLongLocalizedLowercaseString) ?: ""
            }
    }

    override fun onDestroyView() {
        viewBinding.fragmentDrawerSectionsList.adapter = null
        super.onDestroyView()
    }
}
