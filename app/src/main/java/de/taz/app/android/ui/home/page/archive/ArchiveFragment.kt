package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.data.DataService
import de.taz.app.android.databinding.FragmentArchiveBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.home.page.IssueFeedFragment
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment: IssueFeedFragment<FragmentArchiveBinding>() {

    override lateinit var adapter: IssueFeedAdapter
    private lateinit var dataService: DataService

    private val grid by lazy { viewBinding.fragmentArchiveGrid }
    private val toCoverFlow by lazy { viewBinding.fragmentArchiveToCoverFlow }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.pdfModeLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
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

        viewModel.feed.observeDistinct(this) { feed ->
            val requestManager = Glide.with(this)
            val itemLayout = if (viewModel.pdfModeLiveData.value == true) {
                R.layout.fragment_archive_frontpage_item
            } else {
                R.layout.fragment_archive_moment_item
            }
            grid.setHasFixedSize(true)
            adapter = ArchiveAdapter(
                this,
                itemLayout,
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

    override fun onDestroyView() {
        grid.adapter = null
        super.onDestroyView()
    }
}
