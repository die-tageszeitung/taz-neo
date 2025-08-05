package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentArchiveBinding
import de.taz.app.android.monkey.setDefaultTopInset
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedFragment
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : IssueFeedFragment<FragmentArchiveBinding>() {

    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // update the grid if the feed changes
        viewModel.feed
            .flowWithLifecycle(lifecycle)
            .onEach { feed ->
                val requestManager = Glide.with(requireParentFragment())
                adapter = ArchiveAdapter(
                    this,
                    R.layout.fragment_archive_item,
                    feed,
                    requestManager
                )
                viewBinding.fragmentArchiveGrid.adapter = adapter
            }.launchIn(lifecycleScope)
    }

    private val enableRefreshViewOnScrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            viewModel.refreshViewEnabled.value = !recyclerView.canScrollVertically(-1)

            super.onScrolled(recyclerView, dx, dy)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.appBarLayout.setDefaultTopInset()

        viewBinding.fragmentArchiveGrid.addOnScrollListener(
            enableRefreshViewOnScrollListener,
        )

        viewModel.pdfMode
            .drop(1)
            .onEach {
                // redraw all visible views
                viewBinding.fragmentArchiveGrid.adapter?.notifyDataSetChanged()

                // Track a new screen if the PDF mode changes when the Fragment is already resumed.
                // This is necessary in addition to the tracking in onResume because that is not called
                // when we only update the UI.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    tracker.trackArchiveScreen(it)
                }
            }
            .launchIn(lifecycleScope)

        viewBinding.fragmentArchiveGrid.layoutManager = GridLayoutManager(requireContext(), calculateNoOfColumns())
        viewBinding.fragmentArchiveGrid.setHasFixedSize(true)

        viewBinding.representation.setOnClickListener {
            // TODO Show bottomSheetFragment and do not set homeFragmentState directly
            lifecycleScope.launch {
                generalDataStore.homeFragmentState.set(HomeFragment.State.COVERFLOW)
            }
        }
        viewBinding.calendar.setOnClickListener {
            openDatePicker()
        }
    }

    private fun openDatePicker() {
        DatePickerFragment().show(childFragmentManager, DatePickerFragment.TAG)
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
