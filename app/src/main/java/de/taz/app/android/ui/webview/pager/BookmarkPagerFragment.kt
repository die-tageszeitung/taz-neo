package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.audioPlayer.AudioPlayerViewModel
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class BookmarkPagerFragment : BaseViewModelFragment<BookmarkPagerViewModel, FragmentWebviewPagerBinding>() {

    val log by Log

    private lateinit var articlePagerAdapter: BookmarkPagerAdapter
    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var toastHelper: ToastHelper

    private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
        articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked)
    }
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    private val articleBottomActionBarNavigationHelper =
        ArticleBottomActionBarNavigationHelper(::onBottomNavigationItemClicked)


    override val viewModel: BookmarkPagerViewModel by activityViewModels()
    private val issueViewerViewModel: IssueViewerViewModel by activityViewModels()
    private val audioPlayerViewModel: AudioPlayerViewModel by viewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()

        articleBottomActionBarNavigationHelper
            .setBottomNavigationFromContainer(viewBinding.navigationBottomLayout)

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
        }

        viewModel.bookmarkedArticleStubsLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
            articlePagerAdapter.articleStubs = it
            viewBinding.loadingScreen.root.visibility = View.GONE
            tryScrollToArticle()
        }

        viewModel.articleFileNameLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
            tryScrollToArticle()
        }

        // Receiving a displayable on the issueViewerViewModel means user clicked on a section, so we'll open an actual issuecontentviewer instead this pager
        issueViewerViewModel.issueKeyAndDisplayableKeyLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
            if (it != null) {
                requireActivity().apply {
                    startActivity(
                        IssueViewerActivity.newIntent(
                            this,
                            IssuePublication(it.issueKey),
                            it.displayableKey
                        )
                    )
                    finish()
                }
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    audioPlayerViewModel.isActiveAudio.collect {
                        articleBottomActionBarNavigationHelper.setArticleAudioMenuIcon(it)
                    }
                }

                launch {
                    audioPlayerViewModel.isPlayerVisible.collect { isVisible ->
                        if (isVisible) {
                            articleBottomActionBarNavigationHelper.fixToolbar()
                        } else {
                            articleBottomActionBarNavigationHelper.releaseToolbar()
                        }
                    }
                }

                launch {
                    audioPlayerViewModel.errorMessageFlow.filterNotNull().collect { message ->
                        toastHelper.showToast(message, long = true)
                        audioPlayerViewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
            articlePagerAdapter = BookmarkPagerAdapter()
            adapter = articlePagerAdapter
        }

        articlePagerAdapter.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                articlePagerAdapter.getArticleStub(
                    viewBinding.webviewPagerViewpager.currentItem
                )?.let {
                    rebindBottomNavigation(it)
                }
            }
        })
    }

    private fun rebindBottomNavigation(articleToBindTo: ArticleStub) {
        // show the share icon always when in public issues (as it shows a popup that the user should log in)
        // OR when an onLink link is provided
        articleBottomActionBarNavigationHelper.setShareIconVisibility(
            articleToBindTo.onlineLink,
            articleToBindTo.key
        )
        isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
        isBookmarkedLiveData = bookmarkRepository.createBookmarkStateFlow(articleToBindTo.articleFileName).asLiveData()
        isBookmarkedLiveData?.observe(this@BookmarkPagerFragment, isBookmarkedObserver)

    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // If the pager is empty (no bookmarks left) we want to pop it off the stack and return to the last fragment
            if (articlePagerAdapter.articleStubs.isEmpty()) {
                this@BookmarkPagerFragment.parentFragmentManager.popBackStack()
                return
            }
            val articleStub = articlePagerAdapter.getArticleStub(position)
            articleStub?.let {
                viewModel.articleFileNameLiveData.value = it.articleFileName
                rebindBottomNavigation(it)
                audioPlayerViewModel.setIsVisibleArticle(it)
            }


            articleBottomActionBarNavigationHelper.apply {
                // show the player button only for articles with audio
                val hasAudio = articleStub?.hasAudio == true
                setArticleAudioVisibility(hasAudio)

                // ensure the action bar is showing when the article changes
                expand(true)
            }
        }
    }

    private fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> MainActivity.start(requireContext())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentlyDisplayedArticleStub()?.let {
                    viewModel.toggleBookmark(it)
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }

            R.id.bottom_navigation_action_audio -> audioPlayerViewModel.handleOnAudioActionOnVisibleArticle()
        }
    }

    fun share() {
        lifecycleScope.launch {
            getCurrentlyDisplayedArticleStub()?.let { articleStub ->
                val url = articleStub.onlineLink
                url?.let {
                    shareArticle(url, articleStub.title)
                } ?: showSharingNotPossibleDialog()
            }
        }
    }

    private fun tryScrollToArticle() {
        val articleFileName = viewModel.articleFileNameLiveData.value
        if (
            articleFileName?.startsWith("art") == true &&
            viewModel.bookmarkedArticleStubsLiveData.value?.map { it.key }
                ?.contains(articleFileName) == true
        ) {
            log.debug("I will now display $articleFileName")
            lifecycleScope.launchWhenResumed {
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                    }
                }
            }
        }
    }

    private fun getCurrentlyDisplayedArticleStub(): ArticleStub? =
        articlePagerAdapter.getArticleStub(getCurrentPagerPosition())

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position = articlePagerAdapter.articleStubs.indexOfFirst {
            it.key == viewModel.articleFileNameLiveData.value
        }
        return if (position >= 0) {
            position
        } else {
            null
        }
    }

    private fun shareArticle(url: String, title: String?) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let {
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private inner class BookmarkPagerAdapter : FragmentStateAdapter(
        this@BookmarkPagerFragment
    ) {

        var articleStubs: List<ArticleStub> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun createFragment(position: Int): Fragment {
            val article = articleStubs[position]
            val pagerPosition = position + 1
            val pagerTotal = articleStubs.size
            return ArticleWebViewFragment.newInstance(article.articleFileName, pagerPosition, pagerTotal)
        }

        override fun getItemId(position: Int): Long {
            return articleStubs[position].key.hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return articleStubs.any { itemId == it.key.hashCode().toLong() }
        }

        override fun getItemCount(): Int = articleStubs.size


        fun getArticleStub(position: Int): ArticleStub? {
            return articleStubs.getOrNull(position)
        }

    }

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        articleBottomActionBarNavigationHelper.onDestroyView()
        super.onDestroyView()
    }
}