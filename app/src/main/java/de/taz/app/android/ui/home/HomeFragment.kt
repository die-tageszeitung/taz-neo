package de.taz.app.android.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.content.FeedService
import de.taz.app.android.databinding.FragmentHomeBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import kotlinx.coroutines.*
import java.util.*

class HomeFragment : BaseMainFragment<FragmentHomeBinding>() {
    val log by Log

    var onHome: Boolean = true
    private var refreshJob: Job? = null

    private val homePageViewModel: IssueFeedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                homePageViewModel.pdfModeLiveData.observe(viewLifecycleOwner) { pdfMode ->
                    val drawable = if (pdfMode) R.drawable.ic_app_view else R.drawable.ic_pdf_view
                    viewBinding.fabActionPdf.setImageResource(drawable)
                }
            }
        }

        viewBinding.apply {
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
                                setHomeIconFilled(false)
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
                        delay(1000L - (end - start))
                        hideRefreshLoadingIcon()
                    }
                }
                reduceDragSensitivity(10)
            }

            fabActionPdf.setOnClickListener {
                homePageViewModel.togglePdfMode()
            }
        }
    }

    private suspend fun onRefresh() {
        try {
            val feedService = FeedService.getInstance(requireContext().applicationContext)
            feedService.refreshFeed(DISPLAYED_FEED)
        } catch (e: ConnectivityException.NoInternetException) {
            ToastHelper.getInstance(requireContext().applicationContext)
                .showNoConnectionToast()
        } catch (e: ConnectivityException.ImplementationException) {
            ToastHelper.getInstance(requireContext().applicationContext)
                .showSomethingWentWrongToast()
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

    override fun onDestroyView() {
        viewBinding.feedArchivePager.adapter = null
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun setHomeIconFilled(filled: Boolean = true) {
        val menuView = view?.rootView?.findViewById<BottomNavigationView>(R.id.navigation_bottom)
        val menu = menuView?.menu
        if (filled) {
            onHome = true
            menuView?.post {
                menu?.findItem(R.id.bottom_navigation_action_home)
                    ?.setIcon(R.drawable.ic_home_filled)
            }
        } else {
            menuView?.post {
                menu?.findItem(R.id.bottom_navigation_action_home)
                    ?.setIcon(R.drawable.ic_home)
            }
        }
    }

    fun showArchive() {
        viewBinding.feedArchivePager.currentItem = ARCHIVE_PAGER_POSITION
    }

    fun refresh() = viewBinding.coverflowRefreshLayout.setRefreshingWithCallback(true)
}