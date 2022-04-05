package de.taz.app.android.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchResultPagerFragment(var position: Int) : BaseMainFragment(
    R.layout.search_result_webview_pager
) {
    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article
    private lateinit var webViewPager : ViewPager2
    private lateinit var loadingScreen: ConstraintLayout

    val viewModel by activityViewModels<SearchResultPagerViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webViewPager = view.findViewById(R.id.webview_pager_viewpager)
        loadingScreen = view.findViewById(R.id.loading_screen)
        webViewPager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
        loadingScreen.visibility = View.GONE
        setupViewPager()
    }

    private fun setupViewPager() {
        webViewPager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
            adapter = SearchResultPagerAdapter(this@SearchResultPagerFragment, viewModel.searchResultsLiveData.value ?: emptyList())
            setCurrentItem(position, false)
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
            getCurrentSearchHit()?.let { hit ->
                val url: String? = hit.article?.onlineLink
                url?.let {
                    shareArticle(url, hit.title)
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

    private fun getCurrentPagerPosition(): Int {
        return webViewPager.currentItem
    }

    private fun getCurrentSearchHit(): SearchHitDto? {
        return getCurrentPagerPosition().let {
            viewModel.searchResultsLiveData.value?.get(it)
        }
    }

    override fun onDestroyView() {
        webViewPager.adapter = null
        super.onDestroyView()
    }
}