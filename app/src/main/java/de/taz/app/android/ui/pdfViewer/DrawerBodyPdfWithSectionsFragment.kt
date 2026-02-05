package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.taz.app.android.BuildConfig
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.audioPlayer.DrawerAudioPlayerViewModel
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentDrawerBodyPdfWithSectionsBinding
import de.taz.app.android.monkey.setDefaultTopInset
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment.Companion.ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs


/**
 * Fragment used in the drawer to display the currently selected page and the content/articles
 * of an issue.
 */
class DrawerBodyPdfWithSectionsFragment :
    ViewBindingFragment<FragmentDrawerBodyPdfWithSectionsBinding>() {

    private lateinit var storageService: StorageService

    private val pdfPagerViewModel: PdfPagerViewModel by viewModels({ requireParentFragment() })
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()
    private val drawerAudioPlayerViewModel: DrawerAudioPlayerViewModel by viewModels()

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

            // Before API 35 edge-to-edge is not properly supported and the contents draw behind
            // status bar. This fixes it.
            if (Build.VERSION.SDK_INT < 35) {
                ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val margin = resources.getDimensionPixelSize(R.dimen.drawer_margin_top_old_sdk)
                    v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = insets.top + margin
                    }
                    WindowInsetsCompat.CONSUMED
                }
            }

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
    private fun handleArticleClick(pagePosition: Int, article: ArticleOperations) {
        tracker.trackDrawerTapArticleEvent()
        if (!article.isImprint()) {
            pdfPagerViewModel.updateCurrentItem(pagePosition)
        }
        drawerAndLogoViewModel.closeDrawer()
        (requireParentFragment() as? PdfPagerWrapperFragment)?.showArticle(article)
    }

    private fun handleArticleBookmarkClick(article: ArticleOperations) {
        tracker.trackDrawerTapBookmarkEvent()
        toggleBookmark(article)
    }

    private fun handleAudioEnqueueClick(article: ArticleOperations, isEnqueued: Boolean? = false) {
        if (isEnqueued == true) {
            drawerAudioPlayerViewModel.removeFromPlaylist(article.key)
        } else {
            tracker.trackPlaylistEnqueueEvent()
            drawerAudioPlayerViewModel.enqueue(article.key)
        }
    }

    private fun createArticleBookmarkStateFlow(article: ArticleOperations): Flow<Boolean> {
        return bookmarkRepository.createBookmarkStateFlow(article.key)
    }

    private fun toggleBookmark(article: ArticleOperations) {
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
}