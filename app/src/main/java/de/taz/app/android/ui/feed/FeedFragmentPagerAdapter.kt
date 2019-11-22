package de.taz.app.android.ui.feed

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import de.taz.app.android.ui.archive.main.ArchiveFragment
import de.taz.app.android.ui.coverflow.CoverflowFragment

const val FEED_VIEW_FRAGMENT_COUNT = 2

class FeedFragmentPagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    private val coverflowFragment = CoverflowFragment()
    private val archiveFragment = ArchiveFragment()

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> coverflowFragment
            1 -> archiveFragment
            else -> throw IllegalStateException("Invalid position in ViewPager")
        }
    }

    override fun getCount(): Int {
        return FEED_VIEW_FRAGMENT_COUNT
    }
}