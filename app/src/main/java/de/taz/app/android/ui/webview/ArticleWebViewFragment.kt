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
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.*
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.login.fragments.ArticleLoginFragment
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import kotlinx.coroutines.*

class ArticleWebViewFragment : WebViewFragment<ArticleStub>(R.layout.fragment_webview_article) {

    override val viewModel = ArticleWebViewViewModel()
    override val nestedScrollViewId: Int = R.id.nested_scroll_view

    var observer: Observer<Boolean>? = null

    private val fileHelper = FileHelper.getInstance()

    companion object {
        fun createInstance(article: ArticleStub): ArticleWebViewFragment {
            val fragment = ArticleWebViewFragment()
            fragment.displayable = article
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observer = viewModel.isBookmarkedLiveData.observeDistinct(this) { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
    }

    override fun setHeader(displayable: ArticleStub) {
        lifecycleScope.launch(Dispatchers.IO) {
            val index = displayable.getIndexInSection() ?: 0
            val count = ArticleRepository.getInstance().getSectionArticleStubListByArticleName(
                displayable.key
            ).size

            // only the imprint should have no section
            val sectionStub = displayable.getSectionStub()
            setHeaderForSection(index, count, sectionStub)

            val issueOperations = displayable.getIssueOperations()
            issueOperations?.apply {
                if (isWeekend) {
                    FontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)?.let { typeface ->
                        withContext(Dispatchers.Main) {
                            view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                            view?.findViewById<TextView>(R.id.article_num)?.typeface = typeface
                        }
                    }
                }
            }
        }
    }

    private fun setHeaderForSection(index: Int, count: Int, sectionStub: SectionStub?) {
        val title = sectionStub?.title ?: getString(R.string.imprint)
        activity?.runOnUiThread {
            view?.findViewById<TextView>(R.id.section)?.text = title
            view?.findViewById<TextView>(R.id.article_num)?.text = getString(
                R.string.fragment_header_article, index, count
            )
            view?.findViewById<TextView>(R.id.section)?.setOnClickListener {
                goBackToSection(sectionStub)
            }
        }
    }

    private fun goBackToSection(sectionStub: SectionStub?) {
        sectionStub?.let{
            showInWebView(it.key)
        }
    }

    override fun hideLoadingScreen() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.displayable?.let { article ->
                if (article.getIssueStub()?.status == IssueStatus.public) {
                    withContext(Dispatchers.Main) {
                        try {
                            childFragmentManager.beginTransaction().replace(
                                R.id.fragment_article_bottom_fragment_placeholder,
                                ArticleLoginFragment.create(article.key)
                            ).commit()
                        } catch (e: IllegalStateException) {
                            // do nothing already hidden
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    super.hideLoadingScreen()
                }
            }
        }
    }

    fun share() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.displayable?.let { article ->
                val url = article.onlineLink
                url?.let {
                    val title = article.title
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        shareArticle(url, title, article.getFirstImage())
                    } else {
                        shareArticle(url, title)
                    }
                }
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

    @TargetApi(28)
    private fun shareArticle(url: String, title: String?, image: Image?) {
        lifecycleScope.launch(Dispatchers.IO) {
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
                withContext(Dispatchers.Main) {
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                }
            }
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
                share()

            R.id.bottom_navigation_action_size -> {
                showFontSettingBottomSheet()
            }
        }
    }

    private fun showBookmarkBottomSheet() =
        viewModel.displayable?.key?.let {
            showBottomSheet(BookmarkSheetFragment.create(it))
        }

}

