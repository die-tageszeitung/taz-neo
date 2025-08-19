package de.taz.app.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.ArchiveCoachMark
import de.taz.app.android.content.FeedService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentHomeBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.ui.home.page.archive.ArchiveFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

        // allow or forbid user to drag refreshView
        // before user interacts we are in resumed state
        issueFeedViewModel.refreshViewEnabled
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach {
                viewBinding.coverflowRefreshLayout.isEnabled = it
            }.launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            coverflowRefreshLayout.apply {
                setOnRefreshListener {
                    refreshFeedDebounced()
                }
                reduceDragSensitivity(10)
            }
        }

        // show Fragment if state changes and lifecycle in STARTED
        generalDataStore.homeFragmentState.asFlow()
            .onEach {
                withStarted { showFragmentForState(it) }
            }.launchIn(lifecycleScope)

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

    fun skipToHome() = lifecycleScope.launch {
        issueFeedViewModel.requestNewestDateFocus()
    }

    fun refresh() = viewBinding.coverflowRefreshLayout.setRefreshingWithCallback(true)
}