package de.taz.app.android.ui.search

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter


class SearchResultPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private var loadedCount: Int = 0

    fun updateLoadedCount(newCount: Int) {
        if (loadedCount >= newCount) {
            notifyDataSetChanged()
        } else {
            // This would be wrong if there would be a new session with more results on the first
            // session then had been loaded at all by the previous session. But as we can't trigger
            // any new search from the [SearchResultPagerFragment] this won't happen
            val addedItemCount = newCount - loadedCount
            notifyItemRangeInserted(loadedCount - 1, addedItemCount)
        }
        loadedCount = newCount
    }

    override fun getItemCount(): Int = loadedCount

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return itemId in 0 until loadedCount
    }

    override fun createFragment(position: Int): Fragment {
        return SearchResultPagerItemFragment.newInstance(position)
    }
}