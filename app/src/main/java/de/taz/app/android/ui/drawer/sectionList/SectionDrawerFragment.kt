package de.taz.app.android.ui.drawer.sectionList

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.*
import de.taz.app.android.audioPlayer.DrawerAudioPlayerViewModel
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.FragmentDrawerSectionsBinding
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.home.page.CoverViewActionListener
import de.taz.app.android.ui.home.page.MomentViewBinding
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.webview.pager.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

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

    private var momentBinder: MomentViewBinding? = null

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
                bookmarkRepository::createBookmarkStateFlow,
            )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentDrawerSectionsList.apply {
            adapter = sectionListAdapter
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sectionListAdapter.allOpened.collect { allOpened ->
                        if (allOpened) {
                            viewBinding.fragmentDrawerToggleAllSections.apply {
                                setImageResource(R.drawable.ic_chevron_double_up)
                                contentDescription = getString(R.string.fragment_drawer_sections_collapse_all)
                            }
                        } else {
                            viewBinding.fragmentDrawerToggleAllSections.apply {
                                setImageResource(R.drawable.ic_chevron_double_down)
                                contentDescription = getString(R.string.fragment_drawer_sections_expand_all)
                            }
                        }
                    }
                }

                // Either the issueContentViewModel can change the content of this drawer ...
                launch {
                    issueContentViewModel.issueKeyAndDisplayableKeyLiveData.asFlow()
                        .distinctUntilChanged().filterNotNull()
                        .collect { issueKeyWithDisplayableKey ->
                            log.debug("Set issue issueKey from IssueContent")
                            if (!::currentIssueStub.isInitialized || issueKeyWithDisplayableKey.issueKey != currentIssueStub.issueKey) {
                                showIssue(issueKeyWithDisplayableKey.issueKey)
                            }
                        }
                }

                // or the bookmarkpager
                launch {
                    bookmarkPagerViewModel.currentIssueAndArticleLiveData.asFlow()
                        .distinctUntilChanged().filterNotNull().collect { (issueStub, _) ->
                            log.debug("Set issue ${issueStub.issueKey} from BookmarkPager")
                            if (issueStub.issueKey == bookmarkPagerViewModel.currentIssue?.issueKey) {
                                showIssue(issueStub.issueKey)
                            }
                        }
                }

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
            }
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
            // Wait for the first issueStub that matches the required key. This must succeed at some point as the the IssueStub must be present to show the Issue
            currentIssueStub = issueRepository.getStubFlow(issueKey.feedName, issueKey.date, issueKey.status).filterNotNull().first()

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
                fragmentDrawerPlayIssueIcon.setOnClickListener {
                    drawerAudioPlayerViewModel.handleOnPlayAllClicked()
                }
                fragmentDrawerPlayIssueText.setOnClickListener {
                    drawerAudioPlayerViewModel.handleOnPlayAllClicked()
                }
            }

            if (currentIssueStub.dateDownload == null) {
                launchWaitForIssueDownloadComplete(issueKey)
            }

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
                momentBinder = MomentViewBinding(
                    this@SectionDrawerFragment,
                    momentPublication,
                    null,
                    Glide.with(this@SectionDrawerFragment),
                    object : CoverViewActionListener {
                        override fun onImageClicked(coverPublication: AbstractCoverPublication) {
                            tracker.trackDrawerTapMomentEvent()
                            requireActivity().finish()
                        }
                    },
                    observeDownload = false
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
                toastHelper.showToast(R.string.toast_article_bookmarked)
            } else {
                toastHelper.showToast(R.string.toast_article_debookmarked)
            }
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
}
