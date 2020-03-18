package de.taz.app.android.ui.webview

import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.Section
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import kotlinx.android.synthetic.main.fragment_webview_article.*
import kotlinx.coroutines.*

class ArticleWebViewFragment : WebViewFragment<Article>(R.layout.fragment_webview_article) {

    override val viewModel = ArticleWebViewViewModel()

    private val log by Log
    var observer: Observer<Boolean>? = null

    private val fileHelper = FileHelper.getInstance()

    companion object {
        fun createInstance(article: Article): WebViewFragment<Article> {
            val fragment = ArticleWebViewFragment()
            fragment.displayable = article
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.displayable = displayable
        super.onViewCreated(view, savedInstanceState)

        viewModel.scrollPosition?.let {
            nested_scroll_view.scrollY = it
        }

        nested_scroll_view.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            viewModel.scrollPosition = scrollY
        }

        observer = viewModel.isBookmarkedLiveData.observeDistinct(this) { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
    }

    override fun setHeader(displayable: Article) {
        activity?.lifecycleScope?.launch(Dispatchers.IO) {
            viewModel.displayable?.getSection()?.let { section ->
                setHeaderForSection(section)
            }
        }
    }

    private fun setHeaderForSection(section: Section) {
        activity?.runOnUiThread {
            view?.findViewById<TextView>(R.id.section)?.text = section.title
            view?.findViewById<TextView>(R.id.article_num)?.text = getString(
                R.string.fragment_header_article,
                section.articleList.indexOf(viewModel.displayable) + 1,
                section.articleList.size
            )
            view?.findViewById<TextView>(R.id.section)?.setOnClickListener {
                activity?.onBackPressed()
            }
        }
    }

    override fun hideLoadingScreen() {
        super.hideLoadingScreen()
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.displayable?.let { article ->
                if (article.getIssueStub()?.status == IssueStatus.public) {
                    withContext(Dispatchers.Main) {
                        try {
                            childFragmentManager.beginTransaction().replace(
                                R.id.fragment_article_bottom_fragment_placeholder,
                                ArticleLoginFragment.create(article.articleFileName)
                            ).commit()
                        } catch (e: IllegalStateException) {
                            // do nothing already hidden
                        }
                    }
                }
            }
        }
    }

    fun share(url: String, title: String?, image: FileEntry?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            shareArticle(url, title, image)
        } else {
            shareArticle(url, title)
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

    @TargetApi(28)
    private fun shareArticle(url: String, title: String?, image: FileEntry?) {
        view?.let { view ->
            var imageUri: Uri? = null
            val applicationId = view.context.packageName
            image?.let {
                val imageAsFile = fileHelper.getFile(image)
                imageUri = FileProvider.getUriForFile(
                    view.context,
                    "${applicationId}.contentProvider",
                    imageAsFile
                )
            }
            log.debug("image is: $image")
            log.debug("imageUri is: $imageUri")

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, url)

                putExtra(Intent.EXTRA_STREAM, imageUri)
                type = "image/jpg"

                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                title?.let {
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
                // add rich content for android 10+
                putExtra(Intent.EXTRA_TITLE, title ?: url)
                data = imageUri
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }

    override fun onDestroy() {
        observer?.let {
            Transformations.distinctUntilChanged(viewModel.isBookmarkedLiveData).removeObserver(it)
        }
        super.onDestroy()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome()
            }

            R.id.bottom_navigation_action_bookmark -> {
                showBookmarkBottomSheet()
            }

            R.id.bottom_navigation_action_share ->
                viewModel.displayable?.let { article ->
                    article.onlineLink?.let {
                        share(article.onlineLink, article.title, article.imageList.firstOrNull())
                    }
                }

            R.id.bottom_navigation_action_size -> {
                showFontSettingBottomSheet()
            }
        }
    }

    private fun showBookmarkBottomSheet() =
        viewModel.displayable?.articleFileName?.let {
            showBottomSheet(BookmarkSheetFragment.create(it))
        }
}

