package de.taz.app.android.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.*
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.databinding.FragmentHomeBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import java.util.*

class HomeFragment : BaseMainFragment<FragmentHomeBinding>() {
    val log by Log

    private var refreshJob: Job? = null

    private val homePageViewModel: IssueFeedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            homePageViewModel.pdfModeLiveData.observe(viewLifecycleOwner) { pdfMode ->
                navigationBottom.menu.findItem(R.id.bottom_navigation_action_pdf)
                    .setIcon(if (pdfMode) R.drawable.ic_app_view else R.drawable.ic_pdf_view)
            }

            feedArchivePager.apply {
                adapter = HomeFragmentPagerAdapter(childFragmentManager, lifecycle)

                // reduce viewpager2 sensitivity to make the view less finicky
                reduceDragSensitivity(6)
                registerOnPageChangeCallback(object :
                    ViewPager2.OnPageChangeCallback() {
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
            }

            coverflowRefreshLayout.apply {
                setOnRefreshListener {
                    refreshJob?.cancel()
                    refreshJob = lifecycleScope.launchWhenResumed {
                        val start = Date().time
                        onRefresh()
                        val end = Date().time
                        // show animation at least 1000 ms so it looks smoother
                        if (end - start < 1000) {
                            delay(1000 - (end - start))
                        }
                        hideRefreshLoadingIcon()
                    }
                }
                reduceDragSensitivity(10)
            }
        }
    }

    private suspend fun onRefresh() {
        withContext(Dispatchers.IO) {
            try {
                DataService.getInstance(requireContext().applicationContext)
                    .getFeedByName(DISPLAYED_FEED, allowCache = false)?.let {
                        withContext(Dispatchers.Main) {
                            homePageViewModel.setFeed(
                                it
                            )
                        }
                    }
            } catch (e: ConnectivityException.NoInternetException) {
                ToastHelper.getInstance(requireContext().applicationContext)
                    .showNoConnectionToast()
            } catch (e: ConnectivityException.ImplementationException) {
                ToastHelper.getInstance(requireContext().applicationContext)
                    .showSomethingWentWrongToast()
            }
        }
    }

    private fun enableRefresh() {
        viewBinding.coverflowRefreshLayout.isEnabled = true
    }

    private fun disableRefresh() {
        viewBinding.coverflowRefreshLayout.isEnabled = false
    }

    private fun hideRefreshLoadingIcon() {
        viewBinding.coverflowRefreshLayout.isRefreshing = false
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> Intent(
                requireActivity(),
                BookmarkListActivity::class.java
            ).apply { startActivity(this) }
            R.id.bottom_navigation_action_settings -> Intent(
                requireActivity(),
                SettingsActivity::class.java
            ).apply { startActivity(this) }
            R.id.bottom_navigation_action_help -> {
                val intent = Intent(context?.applicationContext, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                startActivity(Intent(intent))
            }
            R.id.bottom_navigation_action_home -> {
                (activity as? MainActivity)?.showHome()
            }
            R.id.bottom_navigation_action_pdf -> {
                CoroutineScope(Dispatchers.Main).launch {
                    homePageViewModel.setPdfMode(!homePageViewModel.getPdfMode())
                }
            }
        }
    }

    override fun onDestroyView() {
        viewBinding.feedArchivePager.adapter = null
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun setHomeIconFilled() = setIcon(R.id.bottom_navigation_action_home, R.drawable.ic_home_filled)

    fun setHomeIcon() = setIcon(R.id.bottom_navigation_action_home, R.drawable.ic_home)

    fun showArchive() {
        viewBinding.feedArchivePager.currentItem = ARCHIVE_PAGER_POSITION
    }

    fun refresh() = viewBinding.coverflowRefreshLayout.setRefreshingWithCallback(true)
}