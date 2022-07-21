package de.taz.app.android.ui.webview.pager

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentWebviewPagerBinding
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ArticlePagerFragment : BaseMainFragment<FragmentWebviewPagerBinding>(), BackFragment {

    private val log by Log

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private lateinit var storageService: StorageService
    private var mediaPlayer: MediaPlayer? = MediaPlayer()
    private var articleRepository: ArticleRepository? = null
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article
    private var hasBeenSwiped = false
    private var isBookmarkedLiveData: LiveData<Boolean>? = null

    private val issueContentViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        )[IssueViewerViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        issueContentViewModel.articleListLiveData.observeDistinct(this.viewLifecycleOwner) { articleStubs ->
            if (
                articleStubs.map { it.key } !=
                (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.map { it.key }
            ) {
                log.debug("New set of articles: ${articleStubs.map { it.key }}")
                viewBinding.webviewPagerViewpager.adapter = ArticlePagerAdapter(articleStubs, this)
                issueContentViewModel.displayableKeyLiveData.value?.let { tryScrollToArticle(it) }
            }
        }

        issueContentViewModel.displayableKeyLiveData.observeDistinct(this.viewLifecycleOwner) {
            if (it != null) {
                tryScrollToArticle(it)
            }
        }


        issueContentViewModel.activeDisplayMode.observeDistinct(this) {
            // reset swiped flag on navigating away from article pager
            if (it != IssueContentDisplayMode.Article) {
                hasBeenSwiped = false
            }
        }
        issueContentViewModel.goNextArticle.observeDistinct(this) {
            if (it) {
                viewBinding.webviewPagerViewpager.currentItem = getCurrentPagerPosition() + 1
                issueContentViewModel.goNextArticle.value = false
            }
        }
        issueContentViewModel.goPreviousArticle.observeDistinct(this) {
            if (it) {
                viewBinding.webviewPagerViewpager.currentItem = getCurrentPagerPosition() - 1
                issueContentViewModel.goPreviousArticle.value = false
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.navigationBottomWebviewPager.visibility = View.GONE
        // Set the tool bar invisible so it is not open the 1st time. It needs to be done here
        // in onViewCreated - when done in xml the 1st click wont be recognized...
        viewBinding.navigationBottomLayout.visibility = View.INVISIBLE
        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()

            (adapter as ArticlePagerAdapter?)?.notifyDataSetChanged()
        }
        viewBinding.loadingScreen.root.visibility = View.GONE

        storageService = StorageService.getInstance(requireContext().applicationContext)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }


    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            registerOnPageChangeCallback(pageChangeListener)
        }
    }


    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
        private var hasAudioObserver = Observer<Boolean> { hasAudio ->
            if (hasAudio) {
                showItem(R.id.bottom_navigation_action_audio)
            } else {
                hideItem(R.id.bottom_navigation_action_audio)
            }
        }
        private var isBookmarkedLiveData: LiveData<Boolean>? = null
        private var hasAudioLiveData: LiveData<Boolean>? = null

        override fun onPageSelected(position: Int) {
            val nextStub =
                (viewBinding.webviewPagerViewpager.adapter as ArticlePagerAdapter).articleStubs[position]
            if (lastPage != null && lastPage != position) {
                // if position has been changed by 1 (swipe to left or right)
                if (abs(position - lastPage!!) == 1) {
                    hasBeenSwiped = true
                }
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                    nextStub
                ) { issueKey, displayable ->
                    log.debug("After swiping select displayable to ${displayable.key} (${displayable.title})")
                    if (issueContentViewModel.activeDisplayMode.value == IssueContentDisplayMode.Article) {
                        issueContentViewModel.setDisplayable(
                            IssueKeyWithDisplayableKey(
                                issueKey,
                                displayable.key
                            ),
                            immediate = true
                        )
                    }
                }
                // reset lastSectionKey as it might have changed the section by swiping
                if (hasBeenSwiped) {
                    issueContentViewModel.lastSectionKey = null
                    // in pdf mode update the corresponding page:
                    if (tag == ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) {
                        val pdfPageWhereArticleBegins = nextStub.pageNameList.first()
                        pdfPagerViewModel.goToPdfPage(pdfPageWhereArticleBegins)
                    }
                }
            }
            lastPage = position

            lifecycleScope.launchWhenResumed {
                // show the share icon always when in public issues (as it shows a popup that the user should log in)
                // OR when an onLink link is provided
                viewBinding.navigationBottom.menu.findItem(R.id.bottom_navigation_action_share).isVisible =
                    determineShareIconVisibility(
                        nextStub.onlineLink,
                        nextStub.key
                    )

                isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                isBookmarkedLiveData =
                    nextStub.isBookmarkedLiveData(requireContext().applicationContext)
                isBookmarkedLiveData?.observe(this@ArticlePagerFragment, isBookmarkedObserver)

                // determine if articleStub has audio file
                hasAudioLiveData?.removeObserver(hasAudioObserver)
                hasAudioLiveData = nextStub.hasAudio(requireContext().applicationContext)
                hasAudioLiveData?.observe(this@ArticlePagerFragment, hasAudioObserver)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        destroyMediaPlayer()
        return if (hasBeenSwiped) {
            lifecycleScope.launch { showSectionOrGoBack() }
            true
        } else {
            return false
        }
    }

    private suspend fun showSectionOrGoBack(): Boolean = withContext(Dispatchers.IO) {
        getCurrentArticleStub()?.let { articleStub ->
            runIfNotNull(
                issueContentViewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                articleStub.getSectionStub(requireContext().applicationContext)
            ) { issueKey, sectionStub ->
                issueContentViewModel.setDisplayable(
                    IssueKeyWithDisplayableKey(
                        issueKey,
                        sectionStub.key
                    )
                )
                true
            }
        }
        false
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home_article -> MainActivity.start(requireActivity())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentArticleStub()?.let {
                    toggleBookmark(it)
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }

            R.id.bottom_navigation_action_audio -> {
                if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                    stopMediaPlayer()
                } else {
                    playAudioOfArticle()
                }
            }
        }
    }

    private fun toggleBookmark(articleStub: ArticleStub) {
        CoroutineScope(Dispatchers.IO).launch {
            if (isBookmarkedLiveData?.value == true) {
                articleRepository?.debookmarkArticle(articleStub)
            } else {
                articleRepository?.bookmarkArticle(articleStub)
            }
        }
    }

    fun share() {
        lifecycleScope.launch(Dispatchers.IO) {
            getCurrentArticleStub()?.let { articleStub ->
                val url = articleStub.onlineLink
                url?.let {
                    shareArticle(url, articleStub.title)
                } ?: showSharingNotPossibleDialog()
            }
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

    private fun tryScrollToArticle(articleKey: String) {
        val articleStubs =
            (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs
        if (
            articleKey.startsWith("art") &&
            articleStubs?.map { it.key }?.contains(articleKey) == true
        ) {
            if (articleKey != getCurrentArticleStub()?.key) {
                log.debug("I will now display $articleKey")
                getSupposedPagerPosition()?.let {
                    if (it >= 0) {
                        viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
                    }
                }
            }
            issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Article)
        }
    }

    private fun getCurrentPagerPosition(): Int {
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position =
            (viewBinding.webviewPagerViewpager.adapter as? ArticlePagerAdapter)?.articleStubs?.indexOfFirst {
                it.key == issueContentViewModel.displayableKeyLiveData.value
            }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentArticleStub(): ArticleStub? {
        return getCurrentPagerPosition()?.let {
            issueContentViewModel.articleListLiveData.value?.get(it)
        }
    }

    private fun getCurrentArticleAudioFile(): FileEntry? {
        return getCurrentArticleStub()?.articleFileName?.let { articleStub ->
            articleRepository?.get(articleStub)
        }?.audioFile
    }

    private fun playAudioOfArticle() {
        if (getCurrentArticleStub()?.hasAudio == true) {
            lifecycleScope.launch(Dispatchers.IO) {
                getCurrentArticleAudioFile()?.let { audioFile ->
                    val baseUrl =
                        getCurrentArticleStub()?.getIssueStub(requireContext().applicationContext)?.baseUrl
                    mediaPlayer?.apply {
                        reset()
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(
                            "$baseUrl/${audioFile.name}"
                        )
                        prepare()
                        start()
                    }
                }
            }
            setIcon(R.id.bottom_navigation_action_audio, R.drawable.ic_audio_filled)
        }
    }

    private fun stopMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer?.stop()
            setIcon(R.id.bottom_navigation_action_audio, R.drawable.ic_audio)
        }
    }

    private fun destroyMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer?.apply {
                stop()
                release()
            }
            mediaPlayer = null
        }
    }

    override fun onDestroyView() {
        viewBinding.webviewPagerViewpager.adapter = null
        if (this.tag == ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) {
            // On device orientation changes the Fragments Activity is already destroyed when we reach the onDestroyView method.
            // Thus we cant initialize a ViewModel instance from onDestroyView. 
            // As the `by activityViewModels()` is called lazily and not being used before, the ViewModel can not be initialized.
            // To prevent the app from crashing in this case we check explicitly that the Activity has not been destroyed yet.
            if (!this.requireActivity().isDestroyed)
                pdfPagerViewModel.hideDrawerLogo.postValue(true)
        }
        destroyMediaPlayer()
        super.onDestroyView()
    }
}