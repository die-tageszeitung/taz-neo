package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.home.page.IssueFeedFragment
import kotlinx.android.synthetic.main.fragment_archive.*
import kotlin.math.floor

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment: IssueFeedFragment(R.layout.fragment_archive) {

    override lateinit var adapter: IssueFeedAdapter
    private lateinit var dataService: DataService

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns())
        }

        fragment_archive_to_cover_flow.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
        }

        viewModel.feed.observeDistinct(this) { feed ->
            val requestManager = Glide.with(this)
            val itemLayout = if (viewModel.pdfMode.value == true) {
                R.layout.fragment_archive_frontpage_item
            } else {
                R.layout.fragment_archive_moment_item
            }
            fragment_archive_grid.setHasFixedSize(true)
            adapter = ArchiveAdapter(
                this,
                itemLayout,
                feed,
                requestManager
            )
            fragment_archive_grid.adapter = adapter
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
        fragment_archive_grid.adapter = null
        super.onDestroyView()
    }
}
