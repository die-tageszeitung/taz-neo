package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.behavior.HideViewOnScrollBehavior.EDGE_BOTTOM
import de.taz.app.android.BuildConfig
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.audioPlayer.DrawerAudioPlayerViewModel
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.coachMarks.BaseCoachMark
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.DrawerBookmarkCoachMark
import de.taz.app.android.coachMarks.DrawerEnqueueCoachMark
import de.taz.app.android.coachMarks.PdfDrawerGoToArticleCoachMark
import de.taz.app.android.coachMarks.PdfDrawerGoToPageCoachMark
import de.taz.app.android.coachMarks.PdfDrawerGoToSectionCoachMark
import de.taz.app.android.coachMarks.PdfDrawerListMomentCoachMark
import de.taz.app.android.coachMarks.PdfDrawerPlayAllCoachMark
import de.taz.app.android.coachMarks.PdfDrawerSwitchViewToPagesCoachMark
import de.taz.app.android.databinding.FragmentDrawerBodyPdfWithSectionsBinding
import de.taz.app.android.monkey.getHideViewOnScrollBehavior
import de.taz.app.android.monkey.setDefaultBottomInset
import de.taz.app.android.monkey.setDefaultTopInset
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment.Companion.ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs


/**
 * Fragment used in the drawer to display the currently selected page and the content/articles
 * of an issue.
 */
class DrawerBodyPdfWithSectionsFragment :
    ViewBindingFragment<FragmentDrawerBodyPdfWithSectionsBinding>() {

    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val pdfPagerViewModel: PdfPagerViewModel by viewModels({ requireParentFragment() })
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()
    private val drawerAudioPlayerViewModel: DrawerAudioPlayerViewModel by viewModels()

    private lateinit var storageService: StorageService
    private lateinit var adapter: PageWithArticlesAdapter
    private lateinit var toastHelper: ToastHelper
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var tracker: Tracker
    var isInitial = true

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onDestroy() {
        drawerAndLogoViewModel.closeDrawer()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.apply {
            root.setDefaultTopInset()
            navigationPageArticleRecyclerView.setDefaultBottomInset()

            if (BuildConfig.IS_LMD) {
                switchDrawerLayout.visibility = View.GONE
            }

            // Shrink the logo on collapsing appbar
            drawerAppBarLayout.apply {
                addOnOffsetChangedListener { _, verticalOffset ->
                    shrinkLogoByOffset(verticalOffset)
                }
            }

            adapter =
                PageWithArticlesAdapter(
                    emptyList(),
                    { pageName -> handlePageClick(pageName) },
                    { pagePosition, article -> handleArticleClick(pagePosition, article) },
                    ::handleArticleBookmarkClick,
                    ::handleAudioEnqueueClick,
                    ::createArticleBookmarkStateFlow
                )

            navigationPageArticleRecyclerView.adapter = adapter

            // The recycler view should keep the sub items recycler views states.
            // With that we can get rid off a wrapping nestedScrollView.
            navigationPageArticleRecyclerView.recycledViewPool.setMaxRecycledViews(
                TYPE_PAGE, 0
            )

            pdfPagerViewModel.issueStubLiveData.observe(viewLifecycleOwner) { issueStub ->
                drawerAudioPlayerViewModel.setIssueStub(issueStub)
                val dateString = setDrawerDate(issueStub)
                activityPdfDrawerDate.text = dateString
            }

            pdfPagerViewModel.itemsToC.observe(
                viewLifecycleOwner
            ) { items ->
                items?.let {
                    updateToc(items)
                }
            }

            pdfPagerViewModel.currentItem.observe(viewLifecycleOwner) { position ->
                adapter.activePosition = position
            }

            activityPdfDrawerFrontPage.setOnClickListener {
                tracker.trackDrawerTapMomentEvent()
                (requireActivity() as? MainActivity)?.showHome()
            }

            switchDrawerLayout.setOnClickListener {
                drawerAndLogoViewModel.setNewDrawer(isNew = false)
            }
            playIssueLayout.setOnClickListener {
                drawerAudioPlayerViewModel.handleOnPlayAllClicked()
            }

            setupFAB()

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        drawerAudioPlayerViewModel.isIssueActiveAudio.collect { isActive ->
                            val imageResource = if (isActive) {
                                R.drawable.ic_audio_filled
                            } else {
                                R.drawable.ic_audio
                            }
                            viewBinding?.fragmentDrawerPlayIssueIcon?.setImageResource(imageResource)
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
    }
    /**
     * Handle the event when an page with [pageName] is clicked.
     *
     * @param pageName: Filename of the page
     */
    private fun handlePageClick(pageName: String) {
        tracker.trackDrawerTapPageEvent()
        drawerAndLogoViewModel.closeDrawer()
        pdfPagerViewModel.goToPdfPage(pageName)
        parentFragmentManager.popBackStack(
            ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
            POP_BACK_STACK_INCLUSIVE
        )
    }

    /**
     * Handle the event when an article is clicked.
     *
     * @param pagePosition Absolute adapter position of article page.
     * @param article Article that was clicked.
     */
    private fun handleArticleClick(pagePosition: Int, article: Article) {
        tracker.trackDrawerTapArticleEvent()
        if (!article.isImprint()) {
            pdfPagerViewModel.updateCurrentItem(pagePosition)
        }
        drawerAndLogoViewModel.closeDrawer()
        (requireParentFragment() as? PdfPagerWrapperFragment)?.showArticle(article)
    }

    private fun handleArticleBookmarkClick(article: Article) {
        tracker.trackDrawerTapBookmarkEvent()
        toggleBookmark(article)
    }

    private fun handleAudioEnqueueClick(article: Article, isEnqueued: Boolean? = false) {
        if (isEnqueued == true) {
            drawerAudioPlayerViewModel.removeFromPlaylist(article.key)
        } else {
            tracker.trackPlaylistEnqueueEvent()
            drawerAudioPlayerViewModel.enqueue(article.key)
        }
    }

    private fun createArticleBookmarkStateFlow(article: Article): Flow<Boolean> {
        return bookmarkRepository.createBookmarkStateFlow(article.key)
    }

    private fun toggleBookmark(article: Article) {
        viewBinding?.root?.let {
            lifecycleScope.launch {
                val isBookmarked = bookmarkRepository.toggleBookmarkAsync(article).await()
                if (isBookmarked) {
                    SnackBarHelper.showBookmarkSnack(
                        context = requireContext(),
                        view = it,
                    )
                } else {
                    SnackBarHelper.showDebookmarkSnack(
                        context = requireContext(),
                        view = it,
                    )
                }
            }
        }
    }

    /**
     * Update the list/table-of-content of an issue in the drawer.
     *
     * This will update the shown pages and their articles listed on each page.
     *
     * @param items List of pages and articles on each page
     */
    private fun updateToc(items: List<PageWithArticlesListItem>) {
        adapter.pages = items

        viewBinding?.apply {
            // Setup drawer header front page
            Glide
                .with(requireContext())
                .load(storageService.getAbsolutePath((items.first() as PageWithArticlesListItem.Page).page.pagePdf))
                .into(activityPdfDrawerFrontPage)

            navigationPageArticleRecyclerView.adapter = adapter
            hideLoadingScreen()
        }

    }

    private fun hideLoadingScreen() {
        viewBinding?.pdfDrawerLoadingScreen?.apply {
            animate()
                .alpha(0f)
                .withEndAction {
                    visibility = View.GONE
                }
                .duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }
    private fun setDrawerDate(issueStub: IssueStub): String? {
        return if (BuildConfig.IS_LMD) {
            DateHelper.stringToLocalizedMonthAndYearString(issueStub.date)
        } else {
            if (issueStub.isWeekend && !issueStub.validityDate.isNullOrBlank()) {
                DateHelper.stringsToWeek2LineString(
                    issueStub.date,
                    issueStub.validityDate
                )
            } else {
                DateHelper.stringToLongLocalized2LineString(issueStub.date)
            }
        }
    }

    var fullWidth = 0
    var fullHeight = 0
    var justSetWidth = 0

    /**
     * we listen for the offset of the [viewBinding.drawerAppBarLayout] and shrink the logo by the
     * amount off scrolled. It is mostly math magic with some twerks.
     */
    private fun shrinkLogoByOffset(verticalOffset: Int) = viewBinding?.apply {
        if (fullHeight == 0 && verticalOffset == 0 && activityPdfDrawerFrontPageCard.height > 0) {
            fullHeight = activityPdfDrawerFrontPageCard.height
            // set the wrapper to have the full height, otherwise the layout won't expand after collapse
            val lp: ViewGroup.LayoutParams =
                activityPdfDrawerHeader.layoutParams
            lp.height = fullHeight
            activityPdfDrawerHeader.setLayoutParams(lp)
            isInitial = false
        }
        if (fullWidth == 0 && verticalOffset == 0) {
            fullWidth = activityPdfDrawerFrontPageCard.width
        }
        if (fullWidth > 0 && fullHeight > 0) {
            val collapsableHeight =
                drawerAppBarLayout.height.toFloat() - toolbar.height.toFloat()
            if (collapsableHeight > 0) {
                val factor = 1 - (abs(verticalOffset) / collapsableHeight)
                val minWidth =
                    resources.getDimensionPixelSize(R.dimen.fragment_drawer_card_width)
                val minHeight =
                    resources.getDimensionPixelSize(R.dimen.fragment_drawer_card_height)
                val newWidth = (minWidth + factor * (fullWidth - minWidth)).toInt()
                val newHeight = (minHeight + factor * (fullHeight - minHeight)).toInt()
                if (newWidth != justSetWidth) {
                    val lp: ViewGroup.LayoutParams =
                        activityPdfDrawerFrontPageCard.layoutParams

                    if (newWidth != lp.width) {
                        lp.width = newWidth
                        lp.height = newHeight
                        activityPdfDrawerFrontPageCard.setLayoutParams(lp)
                    }
                    justSetWidth = newWidth
                }
            }
        }
    }

    private fun setupFAB() {
        viewBinding?.fabHelp?.let { fabHelp ->
            ViewCompat.setOnApplyWindowInsetsListener(fabHelp) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val marginBottomFromDimens =
                    resources.getDimensionPixelSize(R.dimen.fab_margin)
                val bottomBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bottom_height)
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom + bottomBarHeight + marginBottomFromDimens
                }

                // Return CONSUMED if you don't want the window insets to keep passing
                // down to descendant views.
                WindowInsetsCompat.CONSUMED
            }
            fabHelp.setOnClickListener {
                log.verbose("show coach marks in pdf drawer")
                showCoachMarks()
            }

            fabHelp.getHideViewOnScrollBehavior()?.setViewEdge(EDGE_BOTTOM)

            issueContentViewModel.fabHelpEnabledFlow
                .flowWithLifecycle(lifecycle)
                .onEach {
                    fabHelp.isVisible = it
                }.launchIn(lifecycleScope)
        }
    }

    private fun showCoachMarks() {
        val coachMarks = mutableListOf<BaseCoachMark>()

        viewBinding?.apply {
            val firstSection = requireView().findViewById<TextView>(R.id.preview_page_title)
            val firstPage = requireView().findViewById<ImageView>(R.id.preview_page_image)
            val tocItem = requireView().findViewById<ConstraintLayout>(R.id.toc_item)
            val firstArticle = requireView().findViewById<TextView>(R.id.article_title)
            val firstArticleTeaser = requireView().findViewById<TextView>(R.id.article_teaser)
            val firstArticleAuthorMinsString = requireView().findViewById<TextView>(R.id.article_author_and_read_minutes)?.text?.toString() ?: ""
            // Take everything until the first digit from eg "von Anna Arthur 3min"
            val firstArticleAuthor = firstArticleAuthorMinsString.takeWhile { !it.isDigit() }
            // Get the remainder (the "3 min" part)
            val firstArticleMin = firstArticleAuthorMinsString.substringAfter(firstArticleAuthor)

            coachMarks.addAll(
                listOf(
                    PdfDrawerListMomentCoachMark.create(activityPdfDrawerFrontPage),
                    PdfDrawerSwitchViewToPagesCoachMark.create(switchDrawerLayout),
                    PdfDrawerPlayAllCoachMark.create(playIssueLayout),
                    PdfDrawerGoToPageCoachMark.create(firstPage),
                    PdfDrawerGoToArticleCoachMark.create(
                        tocItem,
                        firstArticle.text.toString(),
                        firstArticleTeaser.text.toString(),
                        firstArticleAuthor,
                        firstArticleMin,
                        firstArticle.width
                    ),
                    PdfDrawerGoToSectionCoachMark.create(firstSection),
                    DrawerEnqueueCoachMark(),
                    DrawerBookmarkCoachMark(),
                )
            )
        }

        if (coachMarks.isNotEmpty()) {
            CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
        } else {
            log.debug("coachmarks list is empty")
        }
    }
}