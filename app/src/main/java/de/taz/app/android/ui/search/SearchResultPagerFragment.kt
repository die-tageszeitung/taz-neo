package de.taz.app.android.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.*
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import kotlinx.android.synthetic.main.fragment_webview_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchResultPagerFragment(var position: Int) : BaseMainFragment(
    R.layout.fragment_webview_pager
) {
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article

    val viewModel by activityViewModels<SearchResultPagerViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webview_pager_viewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()

            (adapter as SearchResultPagerAdapter?)?.notifyDataSetChanged()
        }
        loading_screen.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        setupViewPager()
    }

    override fun onResume() {
        super.onResume()
        webview_pager_viewpager.adapter = SearchResultPagerAdapter(viewModel.searchResultsLiveData.value ?: emptyList())
    }


    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            post {
                this.setCurrentItem(position, false)
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> MainActivity.start(requireActivity())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentSearchHit()?.article?.articleHtml?.name?.let {
                    showBottomSheet(BookmarkSheetFragment.create(it))
                }
            }

            R.id.bottom_navigation_action_share ->
                share()

            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }

    fun share() {
        lifecycleScope.launch(Dispatchers.IO) {
            getCurrentSearchHit()?.let { articleStub ->
                val url: String? = null // TODO articleStub.onlineLink
                url?.let {
                    shareArticle(url, articleStub.title)
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

    private fun getCurrentPagerPosition(): Int? {
        return webview_pager_viewpager?.currentItem
    }

    private fun getSupposedPagerPosition(): Int? {
        val position =
            (webview_pager_viewpager.adapter as? SearchResultPagerAdapter)?.searchResultList?.indexOfFirst {
                it.article?.articleHtml?.name == viewModel.articleFileNameLiveData.value
            }
        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    private fun getCurrentSearchHit(): SearchHitDto? {
        return getCurrentPagerPosition()?.let {
            viewModel.searchResultsLiveData.value?.get(it)
        }
    }

    override fun onDestroyView() {
        webview_pager_viewpager.adapter = null
        super.onDestroyView()
    }
}