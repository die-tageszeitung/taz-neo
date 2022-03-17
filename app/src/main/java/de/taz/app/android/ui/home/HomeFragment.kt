package de.taz.app.android.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.DISPLAYED_FEED
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_home.feed_archive_pager
import kotlinx.android.synthetic.main.fragment_home.fab_action_pdf
import kotlinx.android.synthetic.main.fragment_home.coverflow_refresh_layout
import kotlinx.coroutines.*
import java.util.*

class HomeFragment : BaseMainFragment(R.layout.fragment_home) {
    private val log by Log
    var onHome: Boolean = true
    private var refreshJob: Job? = null

    private val homePageViewModel: IssueFeedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        homePageViewModel.pdfModeLiveData.observe(viewLifecycleOwner) { pdfMode ->
            val drawable = if (pdfMode) R.drawable.ic_app_view else R.drawable.ic_pdf_view
            fab_action_pdf.setImageResource(drawable)
        }
        feed_archive_pager.adapter = HomeFragmentPagerAdapter(childFragmentManager, lifecycle)

        // reduce viewpager2 sensitivity to make the view less finicky
        feed_archive_pager.reduceDragSensitivity(6)
        feed_archive_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    COVERFLOW_PAGER_POSITION -> {
                        enableRefresh()
                    }
                    ARCHIVE_PAGER_POSITION -> {
                        disableRefresh()
                    }
                }
            }
        })

        coverflow_refresh_layout.setOnRefreshListener {
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
        coverflow_refresh_layout?.reduceDragSensitivity(10)

        fab_action_pdf.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                homePageViewModel.setPdfMode(!homePageViewModel.getPdfMode())
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

    fun enableRefresh() {
        coverflow_refresh_layout?.isEnabled = true
    }

    fun disableRefresh() {
        coverflow_refresh_layout?.isEnabled = false
    }

    private fun hideRefreshLoadingIcon() {
        coverflow_refresh_layout?.isRefreshing = false
    }

    override fun onDestroyView() {
        feed_archive_pager.adapter = null
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun setHomeIconFilled() {
        onHome = true
        val menuView = view?.rootView?.findViewById<BottomNavigationView>(R.id.navigation_bottom)
        val menu = menuView?.menu
        menuView?.post {
            menu?.findItem(R.id.bottom_navigation_action_home)?.setIcon(R.drawable.ic_home_filled)
        }
    }

    fun setHomeIcon() {
        onHome = false
        val menuView = view?.rootView?.findViewById<BottomNavigationView>(R.id.navigation_bottom)
        val menu = menuView?.menu
        menuView?.post {
            menu?.findItem(R.id.bottom_navigation_action_home)?.setIcon(R.drawable.ic_home)
        }
    }

    fun showArchive() {
        feed_archive_pager.currentItem = ARCHIVE_PAGER_POSITION
        setHomeIcon()
    }

    fun refresh() = coverflow_refresh_layout?.setRefreshingWithCallback(true)
}