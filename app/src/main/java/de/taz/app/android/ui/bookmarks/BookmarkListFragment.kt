package de.taz.app.android.ui.bookmarks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.BuildConfig
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentBookmarksBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.BookmarkPagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkListFragment : BaseMainFragment<FragmentBookmarksBinding>() {

    private var recycleAdapter: BookmarkListAdapter? = null
    private var articleRepository: ArticleRepository? = null
    private var momentRepository: MomentRepository? = null
    private var storageService: StorageService? = null
    private val feedFlow: MutableStateFlow<Feed?> = MutableStateFlow(null)

    private val bookmarkPagerViewModel: BookmarkPagerViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        lifecycleScope.launch {
            feedFlow.value =
                FeedRepository.getInstance(context.applicationContext).get(BuildConfig.DISPLAYED_FEED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recycleAdapter = recycleAdapter
            ?: BookmarkListAdapter(
                ::shareArticle,
                bookmarkPagerViewModel::bookmarkArticle,
                bookmarkPagerViewModel::debookmarkArticle,
                ::goToIssueInCoverFlow,
            )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.drawerMenuList.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this.context)
            adapter = recycleAdapter
        }

        bookmarkPagerViewModel.bookmarkedArticlesLiveData.observe(viewLifecycleOwner) { bookmarks ->
            lifecycleScope.launch {
                recycleAdapter?.setData(getGroupedBookmarks(bookmarks))
                hideLoadingScreen()
            }
        }

        view.findViewById<TextView>(R.id.fragment_header_default_title)
            ?.setText(R.string.fragment_bookmarks_title)
    }

    private fun shareArticle(article: Article) {
        lifecycleScope.launch {
            article.onlineLink?.let { url ->
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, url)
                    putExtra(Intent.EXTRA_SUBJECT, article.title)
                    type = "text/plain"
                }
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                }
            } ?: showSharingNotPossibleDialog()
        }
    }

    private fun goToIssueInCoverFlow(dateString: String) {
        lifecycleScope.launch {
            val feed = feedFlow.filterNotNull().first()
            val issuePublication = IssuePublication(feed.name, dateString)
            MainActivity.start(requireContext(), Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, issuePublication)
        }
    }

    /**
     * This function iterates through the given bookmarks:
     * if a new issueDate is found then we add a [BookmarkListItem.Header] else
     * a [BookmarkListItem.Item] to our result list.
     * @param bookmarks - list of bookmarked articles
     * @return a List of [BookmarkListItem] which can be either a [Article] or a representation of
     * momentImageUri (holding the moment) and the dateString
     */
    private suspend fun getGroupedBookmarks(bookmarks: List<Article>): List<BookmarkListItem> {
        val groupedBookmarks: MutableList<BookmarkListItem> = mutableListOf()

        var lastIssueDate = ""
        for (bookmark in bookmarks) {
            if (bookmark.issueDate != lastIssueDate) {
                val momentImageUri = determineMomentImageUri(bookmark)
                val localizedDateString = determineDateString(bookmark)
                groupedBookmarks.add(
                    BookmarkListItem.Header(momentImageUri, localizedDateString, bookmark.issueDate)
                )
                lastIssueDate = bookmark.issueDate
            }
            groupedBookmarks.add(
                BookmarkListItem.Item(bookmark)
            )
        }
        return groupedBookmarks
    }

    private suspend fun determineMomentImageUri(article: Article): String? {

        val moment = momentRepository?.get(
            article.issueFeedName,
            article.issueDate,
            article.guessIssueStatusByArticleFileName()
        )

        val momentImageUri = moment?.getMomentImage()
            ?.let { FileEntry(it) }
            ?.let {
                storageService?.getFileUri(it)
            }

        return momentImageUri
    }

    private suspend fun determineDateString(article: Article): String {
        val feed = feedFlow.filterNotNull().first()
        val bookmarkDate = DateHelper.stringToDate(article.issueDate)

        val publicationDate = feed.publicationDates.firstOrNull { publicationDate ->
            publicationDate.date == bookmarkDate
        }

        val formattedDate = if (publicationDate?.validity != null) {
            // if we got validity date (eg wochentaz)
            DateHelper.dateToWeekTazNotation(publicationDate.date, publicationDate.validity)
        } else {
            if (BuildConfig.IS_LMD) {
                // for LMd date should be "Ausgabe November 2023"
                resources.getString(
                    R.string.fragment_header_custom_published_date,
                    DateHelper.stringToLocalizedMonthAndYearString(article.issueDate)
                )
            } else {
                DateHelper.stringToLongLocalizedLowercaseString(article.issueDate)
            }
        }
        return formattedDate ?: ""
    }

    private fun hideLoadingScreen() {
        viewBinding.loadingScreen.root.apply {
            animate()
                .alpha(0f)
                .withEndAction {
                    visibility = View.GONE
                }
                .duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }
}