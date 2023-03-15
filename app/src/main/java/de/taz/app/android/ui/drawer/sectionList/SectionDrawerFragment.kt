package de.taz.app.android.ui.drawer.sectionList

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.*
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.FragmentDrawerSectionsBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.page.CoverViewActionListener
import de.taz.app.android.ui.home.page.MomentViewBinding
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.webview.pager.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.*

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : ViewBindingFragment<FragmentDrawerSectionsBinding>() {
    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val viewModel: SectionDrawerViewModel by activityViewModels()
    private val bookmarkPagerViewModel: BookmarkPagerViewModel by activityViewModels()

    private val log by Log

    private lateinit var sectionListAdapter: SectionListAdapter

    private lateinit var issueRepository: IssueRepository
    private lateinit var contentService: ContentService
    private lateinit var momentRepository: MomentRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var toastHelper: ToastHelper

    private lateinit var storageService: StorageService

    private var momentBinder: MomentViewBinding? = null

    private lateinit var currentIssueStub: IssueStub

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contentService = ContentService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        feedRepository = FeedRepository.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionListAdapter =
            SectionListAdapter(
                ::onSectionItemClickListener,
                ::handleArticleClick,
                ::handleArticleBookmarkClick,
                bookmarkRepository::createBookmarkStateFlow
            )
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
                }
            }
        }

        // or the bookmarkpager
        bookmarkPagerViewModel.currentIssueAndArticleLiveData.observeDistinct(this.viewLifecycleOwner) { (issueStub, _) ->
            lifecycleScope.launchWhenResumed {
                log.debug("Set issue ${issueStub.issueKey} from BookmarkPager")
                if (issueStub.issueKey == bookmarkPagerViewModel.currentIssue?.issueKey) {
                    showIssue(issueStub.issueKey)
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentDrawerSectionsList.apply {
            adapter = sectionListAdapter
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
        }
        lifecycleScope.launch {
            sectionListAdapter.allOpened.collect { allOpened ->
                if (allOpened) {
                    viewBinding.fragmentDrawerToggleAllSections.setText(R.string.fragment_drawer_sections_collapse_all)
                } else {
                    viewBinding.fragmentDrawerToggleAllSections.setText(R.string.fragment_drawer_sections_expand_all)
                }
            }
        }
        viewBinding.fragmentDrawerSectionsImprint.setOnClickListener {
            lifecycleScope.launch {
                showImprint()
            }
        }
    }

    private fun onSectionItemClickListener(clickedSection: Section) {
        lifecycleScope.launch {
            issueContentViewModel.setDisplayable(
                currentIssueStub.issueKey,
                clickedSection.key
            )
        }
        viewModel.drawerOpen.postValue(false)
    }

    private suspend fun showIssue(issueKey: IssueKey) = withContext(Dispatchers.Main) {
        try {
            val issueStub =
                contentService.downloadMetadata(
                    IssuePublication(issueKey)
                ) as Issue
            currentIssueStub = IssueStub(issueStub)

            setMomentDate(currentIssueStub)
            showMoment(MomentPublication(currentIssueStub.feedName, currentIssueStub.date))

            val sectionStubs = issueStub.sectionList

            val groupedList: MutableList<SectionDrawerItem> = mutableListOf()

            sectionStubs.forEach { section ->
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

            sectionListAdapter.completeList = groupedList

            view?.scrollY = 0
            view?.animate()?.alpha(1f)?.duration = 500
            viewBinding.fragmentDrawerSectionsImprint.apply {
                val isImprint = issueRepository.getImprint(issueStub.issueKey) != null
                if (isImprint) {
                    visibility = View.VISIBLE
                    viewBinding.separatorLineImprintTop.visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                    viewBinding.separatorLineImprintTop.visibility = View.GONE
                }
            }
            viewBinding.fragmentDrawerToggleAllSections.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    sectionListAdapter.toggleAllSections()
                }
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
                    null,
                    Glide.with(this@SectionDrawerFragment),
                    object : CoverViewActionListener {
                        override fun onImageClicked(coverPublication: AbstractCoverPublication) {
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
        log.debug("handleArticleClick on ${article.title}")
        lifecycleScope.launch {
            issueContentViewModel.setDisplayable(currentIssueStub.issueKey, article.key)
            viewModel.drawerOpen.postValue(false)
        }
    }
    private fun handleArticleBookmarkClick(article: Article) {
        log.debug("handleArticleBookmarkClick on ${article.title}")
        lifecycleScope.launch {
            val isBookmarked = bookmarkRepository.toggleBookmarkAsync(article.key).await()
            if (isBookmarked) {
                toastHelper.showToast(R.string.toast_article_bookmarked)
            } else {
                toastHelper.showToast(R.string.toast_article_debookmarked)
            }
        }
    }
}
