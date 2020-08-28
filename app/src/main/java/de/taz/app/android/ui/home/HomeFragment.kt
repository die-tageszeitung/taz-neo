package de.taz.app.android.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.settings.SettingsFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class HomeFragment : BaseMainFragment(R.layout.fragment_home) {
    val log by Log

    private var refreshJob: Job? = null

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
                    COVERFLOW_PAGER_POSITION -> {
                        enableRefresh()
                    }
                    ARCHIVE_PAGER_POSITION -> {
                        setHomeIcon()
                        disableRefresh()
                    }
                }
            }
        })

        coverflow_refresh_layout.setOnRefreshListener {
            refreshJob?.cancel()
            refreshJob = lifecycleScope.launchWhenResumed {
                val start = DateHelper.now
                onRefresh()
                val end = DateHelper.now
                // show animation at least 1000 ms so it looks smoother
                if (end - start < 1000) {
                    delay(1000 - (end - start))
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
                    .showNoConnectionToast()
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
            R.id.bottom_navigation_action_help -> {
                val intent = Intent(context?.applicationContext, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                startActivity(Intent(intent))
            }
            R.id.bottom_navigation_action_home -> {
                (activity as? MainActivity)?.showHome(
                    skipToCurrentIssue = true
                )
            }
        }
    }

    override fun onDestroyView() {
        feed_archive_pager.adapter = null
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun setHomeIconFilled() {
        view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu?.findItem(
            R.id.bottom_navigation_action_home
        )?.setIcon(R.drawable.ic_home_filled)
    }

    fun setHomeIcon() {
        view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu?.findItem(
            R.id.bottom_navigation_action_home
        )?.setIcon(R.drawable.ic_home)
    }
}