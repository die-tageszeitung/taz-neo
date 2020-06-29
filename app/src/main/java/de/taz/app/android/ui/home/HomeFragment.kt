package de.taz.app.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.settings.SettingsFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class HomeFragment : BaseMainFragment(R.layout.fragment_home) {
    val log by Log

    var dateHelper: DateHelper? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dateHelper = DateHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        feed_archive_pager.adapter = HomeFragmentPagerAdapter(
            childFragmentManager,
            lifecycle
        )

        // reduce viewpager2 sensitivity to make the view less finnicky
        feed_archive_pager.reduceDragSensitivity(6)
        feed_archive_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    COVERFLOW_PAGER_POSITION -> enableRefresh()
                    ARCHIVE_PAGER_POSITION -> disableRefresh()
                }
            }
        })

        coverflow_refresh_layout.setOnRefreshListener {
            lifecycleScope.launchWhenResumed {
                dateHelper?.let { dateHelper ->
                    val start = dateHelper.now
                    onRefresh()
                    val end = dateHelper.now
                    // show animation at least 1000 ms so it looks smoother
                    if (end - start < 1000) {
                        delay(1000 - (end - start))
                    }
                }
                hideRefreshLoadingIcon()
            }
        }
        coverflow_refresh_layout?.reduceDragSensitivity(10)
    }

    private suspend fun onRefresh() {
        withContext(Dispatchers.IO) {
            try {
                val apiService = ApiService.getInstance(activity?.applicationContext)
                FeedRepository.getInstance(activity?.applicationContext)
                    .save(apiService.getFeeds())
                IssueRepository.getInstance(activity?.applicationContext)
                    .saveIfDoNotExist(apiService.getLastIssues())
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                ToastHelper.getInstance(context?.applicationContext)
                    .showToast(R.string.toast_no_internet)
            }
        }
    }


    fun enableRefresh() {
        coverflow_refresh_layout?.isEnabled = true
    }

    fun disableRefresh() {
        coverflow_refresh_layout?.isEnabled = false
    }

    private fun hideRefreshLoadingIcon() {
        coverflow_refresh_layout?.isRefreshing = false
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> showMainFragment(BookmarksFragment())
            R.id.bottom_navigation_action_settings -> showMainFragment(SettingsFragment())
        }
    }
}