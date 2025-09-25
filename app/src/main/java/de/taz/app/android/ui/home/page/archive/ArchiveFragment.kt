package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentArchiveBinding
import de.taz.app.android.monkey.setDefaultTopInset
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DatePickerHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.HomePresentationBottomSheet
import de.taz.app.android.ui.home.page.IssueFeedFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.floor

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : IssueFeedFragment<FragmentArchiveBinding>() {

    private lateinit var authHelper: AuthHelper
    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore

    private val gridLayoutManager by lazy {
        GridLayoutManager(requireContext(), calculateNoOfColumns())
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // update the grid if the feed changes
        viewModel.feed
            .onEach { feed ->
                withStarted {
                    val requestManager = Glide.with(requireParentFragment())
                    adapter = ArchiveAdapter(
                        this,
                        R.layout.fragment_archive_item,
                        feed,
                        requestManager
                    )
                    viewBinding.fragmentArchiveGrid.adapter = adapter
                }
            }.launchIn(lifecycleScope)

        viewModel.pdfModeFlow
            // we only need to redraw if the value changes - therefore drop first value
            .drop(1)
            .onEach {
                withStarted {
                    // redraw all visible views
                    viewBinding.fragmentArchiveGrid.adapter?.notifyDataSetChanged()

                    // Track a new screen if the PDF mode changes when the Fragment is already resumed.
                    // This is necessary in addition to the tracking in onResume because that is not called
                    // when we only update the UI.
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        tracker.trackArchiveScreen(it)
                    }
                }
            }
            .launchIn(lifecycleScope)

        viewModel.requestDateFocus.onEach { scrollToDate(it) }.launchIn(lifecycleScope)
        maybeShowLoginButton()
    }
    /**
     * hide or show login button depending on auth status
     */
    private fun maybeShowLoginButton() {
        // create new flow that indicates if waiting for mail or logged in
        combine(
            authHelper.isPollingForConfirmationEmail.asFlow(),
            authHelper.isLoggedInFlow,
        ) { isPolling, isLoggedIn -> isPolling || isLoggedIn }
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.homeLoginButton.visibility = if (it) View.GONE else View.VISIBLE
            }.launchIn(lifecycleScope)
    }
    private fun scrollToDate(date: Date) {
        val adapter = adapter ?: return

        adapter.getPosition(date).let { position ->
            gridLayoutManager.scrollToPositionWithOffset(position, 0)
        }
    }

    private val enableRefreshViewOnScrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            viewModel.refreshViewEnabled.value = !recyclerView.canScrollVertically(-1)

            super.onScrolled(recyclerView, dx, dy)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {

            collapsingToolbarLayout.setDefaultTopInset()

            appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
                // hide appbar content if it's collapsed
                collapsingToolbarLayout.alpha = 1f- abs(verticalOffset) / collapsingToolbarLayout.height.toFloat()
            }


            fragmentArchiveGrid.addOnScrollListener(
                enableRefreshViewOnScrollListener,
            )


            fragmentArchiveGrid.layoutManager = gridLayoutManager
            fragmentArchiveGrid.setHasFixedSize(true)

            representation.setOnClickListener {
                HomePresentationBottomSheet().show(parentFragmentManager)
            }
            calendar.setOnClickListener {
                openDatePicker()
            }
            homeLoginButton.setOnClickListener { showLoginBottomSheet() }
        }
    }

    private fun openDatePicker() = lifecycleScope.launch {
        val selectedDate = simpleDateFormat.parse(viewModel.feed.first().issueMaxDate) ?: Date()
        val feed = viewModel.feed.first()

        val datePicker =
            DatePickerHelper(selectedDate, feed, viewModel).initializeDatePicker()
        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    override fun onResume() {
        super.onResume()
        trackArchiveScreen()
    }

    private fun trackArchiveScreen() = lifecycleScope.launch {
        tracker.trackArchiveScreen(viewModel.getPdfMode())
    }

    private fun calculateNoOfColumns(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val columnWidth =
            resources.getDimension(R.dimen.fragment_archive_item_width) + resources.getDimension(R.dimen.fragment_archive_navigation_end_padding_horizontal)

        val isLandscape =
            resources.displayMetrics.heightPixels < resources.displayMetrics.widthPixels
        val minColumns = if (isLandscape) 4 else 2
        val itemsFitInRow = floor(screenWidth / columnWidth).toInt()
        return itemsFitInRow.coerceIn(minColumns, 5)
    }
}
