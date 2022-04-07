package de.taz.app.android.ui.search

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.bookmarks.BookmarkSheetFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.main.MainActivity
import kotlinx.android.synthetic.main.fragment_webview_section.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchResultPagerFragment : BaseMainFragment(
    R.layout.search_result_webview_pager
) {
    companion object {
        private const val INITIAL_POSITION = "INITIAL_POSITION"
        fun instance(position: Int) = SearchResultPagerFragment().apply {
            arguments = bundleOf(INITIAL_POSITION to position)
        }
    }

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_article
    private lateinit var webViewPager: ViewPager2
    private lateinit var loadingScreen: ConstraintLayout
    private var initialPosition = 0

    val viewModel by activityViewModels<SearchResultPagerViewModel>()
    private lateinit var tazApiCssDataStore: TazApiCssDataStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webViewPager = view.findViewById(R.id.webview_pager_viewpager)
        loadingScreen = view.findViewById(R.id.loading_screen)
        webViewPager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            moveContentBeneathStatusBar()
        }
        initialPosition = requireArguments().getInt(INITIAL_POSITION, 0)
        viewModel.positionLiveData.value = initialPosition
        loadingScreen.visibility = View.GONE
        setupViewPager()

        val fontSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()
        fontSizeLiveData.observeDistinct(this) {
            reloadAfterCssChange()
        }
    }

    private fun setupViewPager() {
        webViewPager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
        }
        pageChangeCallback.onPageSelected(initialPosition)
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var isBookmarkedObserver = Observer<Boolean> { isBookmarked ->
            if (isBookmarked) {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
            } else {
                setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark)
            }
        }
        private var isBookmarkedLiveData: LiveData<Boolean>? = null

        override fun onPageSelected(position: Int) {
            viewModel.articleFileName = getCurrentSearchHit()?.article?.articleHtml?.name
            super.onPageSelected(position)
            viewModel.positionLiveData.postValue(position)
            if (viewModel.checkIfLoadMore(position)) {
                (activity as SearchActivity).loadMore()
            }
            lifecycleScope.launchWhenResumed {
                isBookmarkedLiveData?.removeObserver(isBookmarkedObserver)
                isBookmarkedLiveData = viewModel.isBookmarkedLiveData
                isBookmarkedLiveData?.observe(this@SearchResultPagerFragment, isBookmarkedObserver)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViewPager.apply {
            if (webViewPager.adapter == null) {
                adapter = SearchResultPagerAdapter(
                    this@SearchResultPagerFragment,
                    viewModel.total,
                    viewModel.searchResultsLiveData.value ?: emptyList()
                )

                setCurrentItem(viewModel.positionLiveData.value ?: initialPosition, false)
            }
            registerOnPageChangeCallback(pageChangeCallback)
        }
    }

    override fun onStop() {
        webViewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onStop()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> MainActivity.start(requireActivity())

            R.id.bottom_navigation_action_bookmark -> {
                getCurrentSearchHit()?.let { hit ->
                    hit.article?.let { article ->
                        showBottomSheet(
                            BookmarkSheetFragment.create(
                                article.articleHtml.name,
                                DateHelper.stringToDate(hit.date)
                            )
                        )
                    }
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

    private fun reloadAfterCssChange() {
        CoroutineScope(Dispatchers.Main).launch {
            web_view?.injectCss()
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N)
                web_view?.reload()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
    }

    override fun onDestroyView() {
        webViewPager.adapter = null
        super.onDestroyView()
    }
}