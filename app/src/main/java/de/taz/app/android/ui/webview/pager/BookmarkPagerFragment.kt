package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.util.Util
import de.taz.app.android.ARTICLE_READER_AUDIO_BACK_SKIP_MS
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch

class BookmarkPagerFragment : BaseViewModelFragment<BookmarkPagerViewModel, FragmentWebviewPagerBinding>() {

    val log by Log

    private lateinit var articlePagerAdapter: BookmarkPagerAdapter
    private lateinit var articleBottomActionBarNavigationHelper: ArticleBottomActionBarNavigationHelper
    private lateinit var articleRepository: ArticleRepository
    private lateinit var bookmarkRepository: BookmarkRepository

    private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
        articleBottomActionBarNavigationHelper.setBookmarkIcon(isBookmarked)
    }
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    override val viewModel: BookmarkPagerViewModel by activityViewModels()
    private val issueViewerViewModel: IssueViewerViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= Build.VERSION_CODES.M) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= Build.VERSION_CODES.M) {
            stopMediaPlayer()
            releasePlayer()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        articleBottomActionBarNavigationHelper = ArticleBottomActionBarNavigationHelper(
            viewBinding.navigationBottom,
            onClickHandler = ::onBottomNavigationItemClicked
        )

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
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
        // Android API level 24 (M) supports multiple windows.
        // So app can be visible but not active in split window mode.
        // Therefore we need to initialize the exo player in onStart (instead of onResume):
        // split window mode>
        if (Util.SDK_INT > Build.VERSION_CODES.M) {
            initializePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        // On Android with split screen support, the player needs to be released in onStop:
        if (Util.SDK_INT > Build.VERSION_CODES.M) {
            stopMediaPlayer()
            releasePlayer()
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
            }

            // if we are showing another article we will stop the player and hide the controls
            stopMediaPlayer()
            // show the player button only for articles with audio
            val hasAudio = articleStub?.hasAudio == true
            articleBottomActionBarNavigationHelper.setArticleAudioVisibility(hasAudio)
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

            R.id.bottom_navigation_action_audio -> onAudioAction()
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
        stopMediaPlayer()
        releasePlayer()
        super.onDestroyView()
    }

    // region audioplayer
    // FIXME (johannes): this is mostly shared 1:1 between ArticlePagerFragment and BookmarkPagerFragment

    private var player: ExoPlayer? = null
    private var bottomBehavior: CoordinatorLayout.Behavior<View>? = null

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(requireContext().applicationContext)
                .setSeekBackIncrementMs(ARTICLE_READER_AUDIO_BACK_SKIP_MS)
                .build()
            viewBinding.playerController.player = player
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        viewBinding.playerController.player = null
    }

    private fun onAudioAction() {
        val articleStub = getCurrentlyDisplayedArticleStub()
        if (articleStub == null) {
            return
        }

        if (articleStub.hasAudio) {
            if (player?.isPlaying == true) {
                stopMediaPlayer()
            } else {
                playAudioOfArticle(articleStub)
            }
        }
    }

    private fun playAudioOfArticle(articleStub: ArticleStub) {
        if (!articleStub.hasAudio) {
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val article = articleRepository.get(articleStub.articleFileName)
            val audioFile = article?.audioFile
            val issueStub = articleStub.getIssueStub(requireContext().applicationContext)
            val currentPlayer = player

            if (article != null && audioFile != null && issueStub != null && currentPlayer != null) {
                fixToolbar()
                showPlayerController()
                viewBinding.playerController.findViewById<TextView>(R.id.title).apply {
                    text = articleStub.title
                }

                val mediaItem = MediaItem.fromUri("${issueStub.baseUrl}/${audioFile.name}")
                currentPlayer.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }
                setArticleAudioMenuItem(isPlaying = true)
            }
        }
    }

    private fun stopMediaPlayer() {
        releaseToolbar()
        hidePlayerController()
        player?.stop()
        setArticleAudioMenuItem(isPlaying = false)
    }

    private fun showPlayerController() {
        val navAndPlayerHeight =
            this.resources.getDimensionPixelSize(R.dimen.nav_bottom_height) + this.resources.getDimensionPixelSize(
                R.dimen.audio_player_bottom_height
            )
        viewBinding.playerController.visibility = View.VISIBLE
        viewBinding.webviewPagerViewpager.setPadding(
            0, 0, 0, navAndPlayerHeight
        )
    }

    private fun hidePlayerController() {
        viewBinding.playerController.visibility = View.GONE
        viewBinding.webviewPagerViewpager.setPadding(0, 0, 0, 0)
    }

    private fun setArticleAudioMenuItem(isPlaying: Boolean) {
        articleBottomActionBarNavigationHelper.setArticleAudioMenuIcon(isPlaying)
    }

    private fun fixToolbar() {
        val params =
            viewBinding.navigationBottomLayout.layoutParams as CoordinatorLayout.LayoutParams
        bottomBehavior = params.behavior
        params.behavior = null
    }

    private fun releaseToolbar() {
        // Restore the previous bottom behavior.
        // The variable bottomBehavior will be null if we never called fixToolbar,
        // or if the bottomBehavior was already null before.
        if (bottomBehavior != null) {
            val params =
                viewBinding.navigationBottomLayout.layoutParams as CoordinatorLayout.LayoutParams
            params.behavior = bottomBehavior
        }
    }

    // endregion

}