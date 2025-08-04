package de.taz.app.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.ArchiveCoachMark
import de.taz.app.android.coachMarks.FabCoachMark
import de.taz.app.android.content.FeedService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentHomeBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.ui.home.page.archive.ArchiveFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date


class HomeFragment : BaseMainFragment<FragmentHomeBinding>() {

    enum class State {
        COVERFLOW,
        ARCHIVE,
    }

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
    private lateinit var generalDataStore: GeneralDataStore

    private val issueFeedViewModel: IssueFeedViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        feedService = FeedService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the latest feed
        val feedFlow = feedService
            .getFeedFlowByName(BuildConfig.DISPLAYED_FEED)
            .distinctUntilChanged { old, new -> Feed.equalsShallow(old, new) }
            .flowWithLifecycle(lifecycle)

        // and propagate it if it is valid.
        feedFlow
            .filterNotNull()
            .onEach {
                issueFeedViewModel.setFeed(it)
            }.launchIn(lifecycleScope)

        // Otherwise (null feed) show a warning to the user.
        feedFlow
            .filter { it == null }
            .onEach {
                val message =
                    "Failed to retrieve feed ${BuildConfig.DISPLAYED_FEED}, cannot show anything"
                log.error(message)
                SentryWrapper.captureMessage(message)
                toastHelper.showSomethingWentWrongToast()
            }.launchIn(CoroutineScope(Dispatchers.Default))


        issueFeedViewModel.pdfMode
            .flowWithLifecycle(lifecycle)
            .drop(1)
            .onEach { showSnackBarIfSwitchingPdfMode(it) }
            .launchIn(lifecycleScope)

        issueFeedViewModel.refreshViewEnabled
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.coverflowRefreshLayout.isEnabled = it
            }.launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // LMD does not offer switching between pdf mode, so no need to observe
        if (BuildConfig.IS_LMD) {
            viewBinding.fabActionPdf.visibility = View.GONE
        } else {
            issueFeedViewModel.pdfMode
                .flowWithLifecycle(lifecycle)
                .onEach { pdfMode ->
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
                }.launchIn(lifecycleScope)
        }

        viewBinding.apply {
            coverflowRefreshLayout.apply {
                setOnRefreshListener {
                    refreshFeedDebounced()
                }
                reduceDragSensitivity(10)
            }

            fabActionPdf.setOnClickListener {
                issueFeedViewModel.togglePdfMode()
                lifecycleScope.launch {
                    FabCoachMark.setFunctionAlreadyDiscovered(requireContext())
                }
            }
        }

        generalDataStore.homeFragmentState.asFlow()
            .flowWithLifecycle(lifecycle)
            .onEach { showFragmentForState(it) }
            .launchIn(lifecycleScope)

        maybeShowCoachMarks()
    }

    private fun showFragmentForState(state: State) {
        val oldFragment = childFragmentManager.findFragmentByTag(state.name)
        val transaction = childFragmentManager.beginTransaction()

        childFragmentManager.fragments.forEach {
            transaction.hide(it)
        }

        if (oldFragment == null) {
            // add fragment if it does not exist
            val fragment = when (state) {
                State.ARCHIVE -> ArchiveFragment()
                State.COVERFLOW -> CoverflowFragment()
            }
            log.debug("adding fragment for state: ${state.name}")

            transaction
                .add(R.id.home_fragment, fragment, state.name)
                .addToBackStack(state.name)
        } else {
            log.debug("showing old fragment for state: ${state.name}")
            transaction
                .show(oldFragment)
        }
        transaction.commit()
    }

    private fun refreshFeedDebounced() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val start = Date().time
                refreshFeed()
                val end = Date().time
                // show animation at least 1000 ms so it looks smoother
                delay(1000L - (end - start))
                hideRefreshLoadingIcon()
            }
        }
    }

    private fun maybeShowCoachMarks() = lifecycleScope.launch {
        FabCoachMark(this@HomeFragment, viewBinding.fabActionPdf)
            .maybeShow()

        ArchiveCoachMark(this@HomeFragment)
            .maybeShow()
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

    private fun showSnackBarIfSwitchingPdfMode(pdfMode: Boolean) {
        val snackTextResId: Int
        if (pdfMode) {
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
            anchorView = viewBinding.fabActionPdf
            setTextMaxLines(4)
            show()
        }
    }

    private suspend fun refreshFeed() {
        try {
            val feedService = FeedService.getInstance(requireContext().applicationContext)
            feedService.refreshFeed(BuildConfig.DISPLAYED_FEED)
            issueFeedViewModel.forceRefresh()
        } catch (e: ConnectivityException.NoInternetException) {
            ToastHelper.getInstance(requireContext().applicationContext)
                .showNoConnectionToast()
        } catch (e: ConnectivityException.ImplementationException) {
            ToastHelper.getInstance(requireContext().applicationContext)
                .showSomethingWentWrongToast()
        }
    }

    private fun hideRefreshLoadingIcon() {
        viewBinding.coverflowRefreshLayout.isRefreshing = false
    }

    override fun onDestroyView() {
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun refresh() = viewBinding.coverflowRefreshLayout.setRefreshingWithCallback(true)
}