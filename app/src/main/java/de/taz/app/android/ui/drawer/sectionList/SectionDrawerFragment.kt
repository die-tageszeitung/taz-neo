package de.taz.app.android.ui.drawer.sectionList

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Section
import de.taz.app.android.audioPlayer.DrawerAudioPlayerViewModel
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.coachMarks.BaseCoachMark
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.SectionDrawerBookmarkCoachMark
import de.taz.app.android.coachMarks.SectionDrawerEnqueueCoachMark
import de.taz.app.android.coachMarks.SectionDrawerMomentCoachMark
import de.taz.app.android.coachMarks.SectionDrawerPlayAllCoachMark
import de.taz.app.android.coachMarks.SectionDrawerSectionCoachMark
import de.taz.app.android.coachMarks.SectionDrawerToggleAllCoachMark
import de.taz.app.android.coachMarks.SectionDrawerToggleOneCoachMark
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.FragmentDrawerSectionsBinding
import de.taz.app.android.monkey.setDefaultVerticalInsets
import de.taz.app.android.persistence.repository.AbstractCoverPublication
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.home.page.CoverViewActionListener
import de.taz.app.android.ui.home.page.CoverViewBinding
import de.taz.app.android.ui.home.page.CoverViewDate
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.BookmarkPagerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : ViewBindingFragment<FragmentDrawerSectionsBinding>() {
    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()
    private val bookmarkPagerViewModel: BookmarkPagerViewModel by activityViewModels()
    private val drawerAudioPlayerViewModel: DrawerAudioPlayerViewModel by viewModels()

    private val log by Log

    private lateinit var sectionListAdapter: SectionListAdapter

    private lateinit var issueRepository: IssueRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var contentService: ContentService
    private lateinit var momentRepository: MomentRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    private var momentBinder: CoverViewBinding? = null
    private var coachMarkMomentBinder: CoverViewBinding? = null

    private lateinit var currentIssueStub: IssueStub

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contentService = ContentService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        sectionRepository = SectionRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionListAdapter =
            SectionListAdapter(
                ::onSectionItemClickListener,
                ::handleSectionToggle,
                ::handleArticleClick,
                ::handleArticleBookmarkClick,
                ::handleEnqueueAudio,
                bookmarkRepository::createBookmarkStateFlow,
            )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.wrapper.setDefaultVerticalInsets()

        viewBinding.fragmentDrawerSectionsList.apply {
            adapter = sectionListAdapter
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
        }

        setupFAB()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sectionListAdapter.allOpened.collect { allOpened ->
                        if (allOpened) {
                            viewBinding.fragmentDrawerToggleAllSections.apply {
                                setImageResource(R.drawable.ic_chevron_double_up)
                                contentDescription =
                                    getString(R.string.fragment_drawer_sections_collapse_all)
                            }
                        } else {
                            viewBinding.fragmentDrawerToggleAllSections.apply {
                                setImageResource(R.drawable.ic_chevron_double_down)
                                contentDescription =
                                    getString(R.string.fragment_drawer_sections_expand_all)
                            }
                        }
                    }
                }

                // Either the issueContentViewModel can change the content of this drawer ...
                launch {
                    issueContentViewModel.issueKeyAndDisplayableKeyFlow
                        .filterNotNull()
                        .collect { issueKeyWithDisplayableKey ->
                            log.debug("Set issue issueKey from IssueContent")
                            if (!::currentIssueStub.isInitialized || issueKeyWithDisplayableKey.issueKey != currentIssueStub.issueKey) {
                                showIssue(issueKeyWithDisplayableKey.issueKey)
                            }
                        }
                }

                // or the bookmarkpager
                bookmarkPagerViewModel.articleFileNameFlow
                    .flowWithLifecycle(lifecycle)
                    .filterNotNull()
                    .onEach { articleFileName ->
                        issueRepository.getIssueStubForArticle(articleFileName)?.let { issueStub ->
                            log.debug("Set issue ${issueStub.issueKey} from BookmarkPager")
                            showIssue(issueStub.issueKey)
                        }
                    }
                    .launchIn(CoroutineScope(Dispatchers.Default))

                launch {
                    drawerAudioPlayerViewModel.isIssueActiveAudio.collect { isActive ->
                        val imageResource = if (isActive) {
                            R.drawable.ic_audio_filled
                        } else {
                            R.drawable.ic_audio
                        }
                        viewBinding.fragmentDrawerPlayIssueIcon.setImageResource(imageResource)
                    }
                }

                launch {
                    drawerAudioPlayerViewModel.errorMessageFlow.filterNotNull().collect { message ->
                        toastHelper.showToast(message, long = true)
                        drawerAudioPlayerViewModel.clearErrorMessage()
                    }
                }

                launch {
                    issueContentViewModel.displayableKeyFlow
                        .collect { key ->
                            sectionListAdapter.currentKey = key
                        }
                }
            }
        }
    }

    private fun showCoachMarks() {
        val coachMarks = mutableListOf<BaseCoachMark>()

        coachMarkMomentBinder?.let {
            val momentCoachMark = SectionDrawerMomentCoachMark.create(
                viewBinding.fragmentDrawerSectionsMoment,
                it,
            )
            coachMarks.add(momentCoachMark)
        } ?: {
            SentryWrapper.captureMessage("Unable to create SectionDrawerMomentCoachMark - binding is null")
        }

        // don't worry - we only show the FAB button if the sections are filled in so the id is
        // found. See showIssue()
        val firstSection = requireView().findViewById<TextView>(R.id.fragment_drawer_section_title)

        coachMarks.addAll(
            listOf(
                SectionDrawerToggleAllCoachMark.create(viewBinding.fragmentDrawerToggleAllSections),
                SectionDrawerPlayAllCoachMark.create(viewBinding.fragmentDrawerPlayIssueLayout),
                SectionDrawerSectionCoachMark.create(firstSection),
                SectionDrawerToggleOneCoachMark(),
                SectionDrawerEnqueueCoachMark(),
                SectionDrawerBookmarkCoachMark(),
            )
        )

        if (coachMarks.isNotEmpty()) {
            CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
        }
    }

    private fun onSectionItemClickListener(clickedSection: Section) {
        tracker.trackDrawerTapSectionEvent()
        lifecycleScope.launch {
            issueContentViewModel.setDisplayable(
                currentIssueStub.issueKey,
                clickedSection.key
            )
        }
        drawerAndLogoViewModel.closeDrawer()
    }

    private fun handleSectionToggle() {
        tracker.trackDrawerToggleSectionEvent()
    }

    private suspend fun showIssue(issueKey: IssueKey) = withContext(Dispatchers.Main) {
        try {
            viewBinding.fabHelp.isVisible = false
            // Wait for the first issueStub that matches the required key. This must succeed at some point as the the IssueStub must be present to show the Issue
            currentIssueStub =
                issueRepository.getStubFlow(issueKey.feedName, issueKey.date, issueKey.status)
                    .filterNotNull().first()

            setMomentDate(currentIssueStub)
            showMoment(MomentPublication(currentIssueStub.feedName, currentIssueStub.date))
            drawerAudioPlayerViewModel.setIssueStub(currentIssueStub)

            val sectionList = sectionRepository.getSectionsForIssue(currentIssueStub.issueKey)
            sectionListAdapter.initWithList(
                getSectionDrawerItemList(sectionList)
            )

            view?.scrollY = 0
            view?.animate()?.alpha(1f)?.duration = 500
            viewBinding.fragmentDrawerSectionsImprint.apply {
                val imprint = issueRepository.getImprintStub(issueKey)
                if (imprint != null) {
                    visibility = View.VISIBLE
                    viewBinding.separatorLineImprintTop.visibility = View.VISIBLE
                    setOnClickListener {
                        tracker.trackDrawerTapImprintEvent()
                        showImprint(issueKey, imprint.key)
                    }
                } else {
                    visibility = View.GONE
                    viewBinding.separatorLineImprintTop.visibility = View.GONE
                }
            }
            viewBinding.apply {
                fragmentDrawerHeaderActionGroup.visibility = View.VISIBLE
                fragmentDrawerToggleAllSectionsTouchArea.setOnClickListener {
                    tracker.trackDrawerToggleAllSectionsEvent()
                    sectionListAdapter.toggleAllSections()
                }
                fragmentDrawerPlayIssueLayout.setOnClickListener {
                    drawerAudioPlayerViewModel.handleOnPlayAllClicked()
                }
            }

            if (currentIssueStub.dateDownload == null) {
                launchWaitForIssueDownloadComplete(issueKey)
            }

            viewBinding.fabHelp.isVisible = issueContentViewModel.fabHelpEnabledFlow.first()
        } catch (e: ConnectivityException.Recoverable) {
            // do nothing we can not load the issueStub as not in database yet.
            // TODO wait for internet and show it once internet is available
        }
    }

    private suspend fun showMoment(momentPublication: MomentPublication?) =
        withContext(Dispatchers.Main) {
            if (momentPublication == null) {
                momentBinder?.unbind()
                return@withContext
            }
            val moment = try {
                contentService.downloadMetadata(momentPublication) as Moment
            } catch (e: CacheOperationFailedException) {
                log.warn("Cache miss and failed download for moment $momentPublication", e)
                SentryWrapper.captureException(e)
                return@withContext
            }
            try {
                contentService.downloadToCache(moment)
                momentBinder = CoverViewBinding(
                    this@SectionDrawerFragment,
                    momentPublication,
                    CoverViewDate(momentPublication.date, momentPublication.date),
                    Glide.with(this@SectionDrawerFragment),
                    object : CoverViewActionListener {
                        override fun onImageClicked(coverPublication: AbstractCoverPublication) {
                            tracker.trackDrawerTapMomentEvent()
                            (requireActivity() as? MainActivity)?.showHome()
                        }
                    },
                    observeDownloads = false
                )
                coachMarkMomentBinder = CoverViewBinding(
                    this@SectionDrawerFragment,
                    momentPublication,
                    CoverViewDate(momentPublication.date, momentPublication.date),
                    Glide.with(this@SectionDrawerFragment),
                    object : CoverViewActionListener {},
                    observeDownloads = false,
                )
                momentBinder?.prepareDataAndBind(viewBinding.fragmentDrawerSectionsMoment)

            } catch (e: CacheOperationFailedException) {
                requireActivity().showIssueDownloadFailedDialog(moment.issueKey)
            }
        }

    private fun showImprint(issueKey: IssueKey, displayableKey: String) {
        lifecycleScope.launch {
            issueContentViewModel.setDisplayable(issueKey, displayableKey)
        }
        drawerAndLogoViewModel.closeDrawer()
    }


    private fun setMomentDate(issueStub: IssueStub?) {
        viewBinding.fragmentDrawerSectionsDate.text =
            if (issueStub?.isWeekend == true
                && !issueStub.validityDate.isNullOrBlank()
            ) {
                DateHelper.stringsToWeek2LineShortString(issueStub.date, issueStub.validityDate)
            } else {
                issueStub?.date?.let(DateHelper::stringToLongLocalized2LineShortString) ?: ""
            }
    }

    override fun onDestroyView() {
        viewBinding.fragmentDrawerSectionsList.adapter = null
        super.onDestroyView()
    }

    /**
     * Handle the event when an article is clicked.
     *
     * @param article Article that was clicked.
     */
    private fun handleArticleClick(article: Article) {
        tracker.trackDrawerTapArticleEvent()
        lifecycleScope.launch {
            issueContentViewModel.setDisplayable(currentIssueStub.issueKey, article.key)
            drawerAndLogoViewModel.closeDrawer()
        }
    }

    private fun handleArticleBookmarkClick(article: Article) {
        tracker.trackDrawerTapBookmarkEvent()
        lifecycleScope.launch {
            val isBookmarked = bookmarkRepository.toggleBookmarkAsync(article).await()
            if (isBookmarked) {
                SnackBarHelper.showBookmarkSnack(
                    context = requireContext(),
                    view = viewBinding.root,
                )
            } else {
                SnackBarHelper.showDebookmarkSnack(
                    context = requireContext(),
                    view = viewBinding.root,
                )
            }
        }
    }

    private fun handleEnqueueAudio(article: Article, alreadyInPlaylist: Boolean) {
        if (alreadyInPlaylist) {
            drawerAudioPlayerViewModel.removeFromPlaylist(article.key)
        } else {
            drawerAudioPlayerViewModel.enqueue(article.key)
        }
    }

    private fun getSectionDrawerItemList(sectionList: List<Section>): MutableList<SectionDrawerItem> {
        val groupedList: MutableList<SectionDrawerItem> = mutableListOf()

        sectionList.forEach { section ->
            groupedList.add(
                SectionDrawerItem.Header(section)
            )
            val articleStubs = section.articleList
            articleStubs.forEach {
                groupedList.add(
                    SectionDrawerItem.Item(it)
                )
            }
        }
        return groupedList
    }

    private fun launchWaitForIssueDownloadComplete(issueKey: IssueKey) {
        viewLifecycleOwner.lifecycleScope.launch {
            val issueDownloadedForSure: IssueStub =
                issueRepository.getStubFlow(issueKey.feedName, issueKey.date, issueKey.status)
                    .filterNotNull()
                    .first { it.dateDownload != null }

            currentIssueStub = issueDownloadedForSure
            val sectionList = sectionRepository.getSectionsForIssue(currentIssueStub.issueKey)
            sectionListAdapter.updateListData(
                getSectionDrawerItemList(sectionList)
            )
        }
    }

    /**
     * On edge to edge we need to properly update the margins of the FAB:
     */
    private fun setupFAB() {
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.fabHelp) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            val marginBottomFromDimens = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginBottomFromDimens
            }

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        viewBinding.fabHelp.setOnClickListener {
            log.verbose("show coach marks in section drawer")
            showCoachMarks()
        }

        issueContentViewModel.fabHelpEnabledFlow
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.fabHelp.isVisible = it
            }.launchIn(lifecycleScope)
    }
}
