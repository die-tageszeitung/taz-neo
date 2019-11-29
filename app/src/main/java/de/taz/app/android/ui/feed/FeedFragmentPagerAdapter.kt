package de.taz.app.android.ui.feed

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.taz.app.android.ui.archive.main.ArchiveFragment
import de.taz.app.android.ui.coverflow.CoverflowFragment

const val FEED_VIEW_FRAGMENT_COUNT = 2

const val COVERFLOW_PAGER_POSITION = 0
const val ARCHIVE_PAGER_POSITION = 1

class FeedFragmentPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int {
        return FEED_VIEW_FRAGMENT_COUNT
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            COVERFLOW_PAGER_POSITION -> CoverflowFragment()
            ARCHIVE_PAGER_POSITION -> ArchiveFragment()
            else -> throw IllegalStateException("Invalid position in ViewPager")
        }
    }
}