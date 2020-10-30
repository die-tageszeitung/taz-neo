package de.taz.app.android.ui.home.page.archive

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.home.page.IssueFeedPagingAdapter
import kotlinx.android.synthetic.main.fragment_archive.*

/**
 * Fragment to show the archive - a GridView of available issues
 */
class ArchiveFragment : HomePageFragment(R.layout.fragment_archive) {

    override lateinit var adapter: IssueFeedPagingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = ArchiveAdapter(
            this,
            R.layout.fragment_archive_item
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_archive_grid.adapter = adapter

        context?.let { context ->
            fragment_archive_grid.layoutManager =
                GridLayoutManager(context, calculateNoOfColumns(context))
        }

        fragment_archive_to_cover_flow.setOnClickListener {
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
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
