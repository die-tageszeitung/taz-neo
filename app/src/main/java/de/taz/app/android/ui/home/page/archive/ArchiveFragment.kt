package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentArchiveBinding
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.monkey.setDefaultBottomInset
import de.taz.app.android.monkey.setDefaultInsets
import de.taz.app.android.monkey.setDefaultTopInset
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedFragment
import kotlin.math.floor

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : IssueFeedFragment<FragmentArchiveBinding>() {

    private lateinit var tracker: Tracker

    private val grid by lazy { viewBinding.fragmentArchiveGrid }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.pdfModeLiveData.observeDistinctIgnoreFirst(viewLifecycleOwner) {
            // redraw all visible views
            viewBinding.fragmentArchiveGrid.adapter?.notifyDataSetChanged()

            // Track a new screen if the PDF mode changes when the Fragment is already resumed.
            // This is necessary in addition to the tracking in onResume because that is not called
            // when we only update the UI.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                tracker.trackArchiveScreen(it)
            }
        }

        context?.let { context ->
            grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns())
        }

        viewModel.feed.observe(viewLifecycleOwner) { feed ->
            val requestManager = Glide.with(this)
            grid.setHasFixedSize(true)
            adapter = ArchiveAdapter(
                this,
                R.layout.fragment_archive_item,
                feed,
                requestManager
            )
            grid.adapter = adapter
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackArchiveScreen(viewModel.pdfModeLiveData.value ?: false)
    }

    private fun calculateNoOfColumns(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val columnWidth =
            resources.getDimension(R.dimen.fragment_archive_item_width) + resources.getDimension(R.dimen.fragment_archive_navigation_end_padding_horizontal)

        val isLandscape = resources.displayMetrics.heightPixels < resources.displayMetrics.widthPixels
        val minColumns = if (isLandscape) 4 else 2
        val itemsFitInRow = floor(screenWidth / columnWidth).toInt()
        return itemsFitInRow.coerceIn(minColumns, 5)
    }
}
