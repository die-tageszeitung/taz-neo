package de.taz.app.android.ui.home.page.archive

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentArchiveBinding
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.ui.home.page.IssueFeedFragment
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : IssueFeedFragment<FragmentArchiveBinding>() {

    private val grid by lazy { viewBinding.fragmentArchiveGrid }
    private val toCoverFlow by lazy { viewBinding.fragmentArchiveToCoverFlow }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.pdfModeLiveData.observeDistinctIgnoreFirst(viewLifecycleOwner) {
                    // redraw all visible views
                    viewBinding.fragmentArchiveGrid.adapter?.notifyDataSetChanged()
                }
            }
        }

        context?.let { context ->
            grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns())
        }

        toCoverFlow.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
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
