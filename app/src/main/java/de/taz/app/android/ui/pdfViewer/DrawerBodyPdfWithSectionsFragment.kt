package de.taz.app.android.ui.pdfViewer

import PageWithArticlesAdapter
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.EmptySignature
import com.bumptech.glide.signature.ObjectKey
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.audioPlayer.DrawerAudioPlayerViewModel
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentDrawerBodyPdfWithSectionsBinding
import de.taz.app.android.monkey.setDefaultBottomInset
import de.taz.app.android.monkey.setDefaultTopInset
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment.Companion.ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.constraintLayout.setDefaultTopInset()
        viewBinding.navigationPageArticleRecyclerView.setDefaultBottomInset()

        adapter =
            PageWithArticlesAdapter(
                emptyList(),
                { pageName -> handlePageClick(pageName) },
                { pagePosition, article -> handleArticleClick(pagePosition, article) },
                ::handleArticleBookmarkClick,
                ::handleAudioEnqueueClick,
                ::createArticleBookmarkStateFlow
            )

        viewBinding.navigationPageArticleRecyclerView.layoutManager = LinearLayoutManager(context)
        viewBinding.navigationPageArticleRecyclerView.adapter = adapter

        pdfPagerViewModel.currentPage.observe(viewLifecycleOwner) {
            pdfPagerViewModel.currentItem.value?.let {
                refreshCurrentPage()
            }
        }

        pdfPagerViewModel.issueStubLiveData.observe(viewLifecycleOwner) { issueStub ->
            drawerAudioPlayerViewModel.setIssueStub(issueStub)
            val dateString = DateHelper.stringToLocalizedMonthAndYearString(issueStub.date)
            viewBinding.fragmentDrawerBodyPdfWithSectionsTitle.text = dateString
        }


        pdfPagerViewModel.itemsToC.observe(
            viewLifecycleOwner
        ) { items ->
            items?.let {
                updateToc(items)
            }
        }

        viewBinding.fragmentDrawerBodyPdfWithSectionsCurrentPageImage.setOnClickListener {
            tracker.trackDrawerTapPageEvent()
            goToCurrentPage()
        }

        viewBinding.fragmentDrawerBodyPdfWithSectionsCurrentPageTitle.setOnClickListener {
            tracker.trackDrawerTapPageEvent()
            goToCurrentPage()
        }
        viewBinding.playIssueLayout.setOnClickListener {
            drawerAudioPlayerViewModel.handleOnPlayAllClicked()
        }
    }

    private fun goToCurrentPage() {
        pdfPagerViewModel.currentItem.value?.let { position ->
            drawerAndLogoViewModel.closeDrawer()
            pdfPagerViewModel.updateCurrentItem(position)
            parentFragmentManager.popBackStack(
                ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
                POP_BACK_STACK_INCLUSIVE
            )
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
        pdfPagerViewModel.updateCurrentItem(pagePosition)
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

    /**
     * Update the list/table-of-content of an issue in the drawer.
     *
     * This will update the shown pages and their articles listed on each page.
     *
     * @param items List of pages and articles on each page
     */
    private fun updateToc(items: List<PageWithArticlesListItem>) {
        adapter = PageWithArticlesAdapter(
            items,
            { pageName -> handlePageClick(pageName) },
            { pagePosition, article -> handleArticleClick(pagePosition, article) },
            ::handleArticleBookmarkClick,
            ::handleAudioEnqueueClick,
            ::createArticleBookmarkStateFlow
        )
        viewBinding.navigationPageArticleRecyclerView.adapter = adapter
        hideLoadingScreen()
    }

    /**
     * Refreshes what page is shown as the current page in the drawer.
     */
    private fun refreshCurrentPage() {
        pdfPagerViewModel.currentPage.value?.let { currentPage ->
            storageService.getAbsolutePath(currentPage.pagePdf)?.let { pagePdfUri ->
                val signature = currentPage.pagePdf.dateDownload
                    ?.let { ObjectKey(it.time) }
                    ?: EmptySignature.obtain()
                Glide
                    .with(this)
                    .load(pagePdfUri)
                    .signature(signature)
                    .into(viewBinding.fragmentDrawerBodyPdfWithSectionsCurrentPageImage)
            }

            viewBinding.fragmentDrawerBodyPdfWithSectionsCurrentPageTitle.text = getString(
                R.string.fragment_header_article_pagina,
                pdfPagerViewModel.currentPage.value?.pagina?.split("-")?.get(0)
            )
        }
    }

    private fun hideLoadingScreen() {
        viewBinding.pdfDrawerLoadingScreen.root.apply {
            animate()
                .alpha(0f)
                .withEndAction {
                    visibility = View.GONE
                }
                .duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }
}