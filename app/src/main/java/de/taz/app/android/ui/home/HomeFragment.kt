package de.taz.app.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.ArchiveCoachMark
import de.taz.app.android.coachMarks.FabCoachMark
import de.taz.app.android.content.FeedService
import de.taz.app.android.databinding.FragmentHomeBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date


class HomeFragment : BaseMainFragment<FragmentHomeBinding>() {
    val log by Log

    var onHome: Boolean = true
    private var refreshJob: Job? = null


    // There seems to be a race condition between the Java GC and the Snackbar handling code,
    // that can result in Snackbars not being shown if they are triggered with a certain timing.
    // Following https://stackoverflow.com/a/45219027 it seems to help to retain a reference to the last Snackbar.
    // To prevent memory losses (the Snackbar holds a reference to the full View hierarchy) we clear it onResume.
    private var lastSnack: Snackbar? = null

    private lateinit var feedService: FeedService
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    private val homePageViewModel: IssueFeedViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        feedService = FeedService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get feed and propagate it to the viewModel
        lifecycleScope.launch {
            // Get the latest feed and propagate it if it is valid.
            // Otherwise (null feed) show a warning to the user.
            // Warning: This will re-try to request the feed from the api indefinitely in case of connection failures.
            feedService
                .getFeedFlowByName(BuildConfig.DISPLAYED_FEED, retryOnFailure = true)
                .distinctUntilChanged { old, new -> Feed.equalsShallow(old, new) }
                .collect {
                    if (it != null) {
                        homePageViewModel.setFeed(it)
                    } else {
                        val message =
                            "Failed to retrieve feed ${BuildConfig.DISPLAYED_FEED}, cannot show anything"
                        log.error(message)
                        SentryWrapper.captureMessage(message)
                        toastHelper.showSomethingWentWrongToast()
                    }
                }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (BuildConfig.IS_LMD) {
            viewBinding.fabActionPdf.visibility = View.GONE
        } else {
            homePageViewModel.pdfModeLiveData.observe(viewLifecycleOwner) { pdfMode ->
                val drawable = if (pdfMode) R.drawable.ic_app_view else R.drawable.ic_pdf_view
                val contentDescription =
                    if (pdfMode) {
                        resources.getString(R.string.bottom_navigation_action_app_view)
                    } else {
                        resources.getString(R.string.bottom_navigation_action_pdf)
                    }
                viewBinding.fabActionPdf.apply {
                    setImageResource(drawable)
                    setContentDescription(contentDescription)
                }
            }
        }

        viewBinding.apply {
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
                val snackTextResId: Int
                if (homePageViewModel.getPdfMode()) {
                    tracker.trackSwitchToMobileModeEvent()
                    snackTextResId = R.string.toast_switch_to_mobile
                } else {
                    tracker.trackSwitchToPdfModeEvent()
                    snackTextResId = R.string.toast_switch_to_pdf
                }


                lastSnack = Snackbar.make(
                    viewBinding.root,
                    // Use getText to parse HTML tags used for string formatting
                    getText(snackTextResId),
                    Snackbar.LENGTH_SHORT
                ).apply {
                    anchorView = fabActionPdf
                    setTextMaxLines(4)
                    show()
                }

                homePageViewModel.togglePdfMode()
                lifecycleScope.launch {
                    FabCoachMark.setFunctionAlreadyDiscovered(requireContext())
                }
            }
        }
        lifecycleScope.launch {

            FabCoachMark(this@HomeFragment, viewBinding.fabActionPdf)
                .maybeShow()

            ArchiveCoachMark(this@HomeFragment)
                .maybeShow()
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Home
        )
    }

    override fun onPause() {
        super.onPause()
        lastSnack = null
    }

    private suspend fun onRefresh() {
        try {
            val feedService = FeedService.getInstance(requireContext().applicationContext)
            feedService.refreshFeed(BuildConfig.DISPLAYED_FEED)
            homePageViewModel.forceRefresh()
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
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun showArchive() {
        lifecycleScope.launch {
            ArchiveCoachMark.setFunctionAlreadyDiscovered(requireContext())
        }
    }

    fun refresh() = viewBinding.coverflowRefreshLayout.setRefreshingWithCallback(true)
}