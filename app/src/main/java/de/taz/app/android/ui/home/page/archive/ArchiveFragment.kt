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
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import kotlinx.android.synthetic.main.fragment_archive.*

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : HomePageFragment(R.layout.fragment_archive) {

    override lateinit var adapter: IssueFeedAdapter
    private lateinit var dataService: DataService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataService = DataService.getInstance()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns(context))
        }

        fragment_archive_to_cover_flow.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
        }

        viewModel.feed.observeDistinct(this) { feed ->
            val requestManager = Glide.with(this@ArchiveFragment)
            adapter = ArchiveAdapter(
                this@ArchiveFragment,
                R.layout.fragment_archive_item,
                feed,
                requestManager
            )
            fragment_archive_grid.adapter = adapter
        }
    }

    private fun calculateNoOfColumns(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        val columnWidthDp =
            resources.getDimension(R.dimen.fragment_archive_item_width) / displayMetrics.density
        return (screenWidthDp / columnWidthDp).toInt()
    }

    override fun onDestroyView() {
        fragment_archive_grid.adapter = null
        super.onDestroyView()
    }
}
