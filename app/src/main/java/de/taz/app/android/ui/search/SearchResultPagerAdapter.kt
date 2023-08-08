package de.taz.app.android.ui.search

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter


class SearchResultPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private var searchResults: SearchResults? = null

    fun updateSearchResults(newSearchResults: SearchResults) {
        if (searchResults?.sessionId == newSearchResults.sessionId) {
            val oldSize = searchResults?.results?.size ?: 0
            val newSize = newSearchResults.results.size
            searchResults = newSearchResults

            when {
                oldSize < newSize -> notifyItemRangeInserted(oldSize, newSize - oldSize)
                oldSize > newSize -> notifyDataSetChanged()
                // oldSize == newSize -> no items have changed. we just keep the old data
            }
        } else {
            searchResults = newSearchResults
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        return searchResults?.loadedResults ?: 0
    }

    override fun createFragment(position: Int): Fragment {
        return SearchResultPagerItemFragment.newInstance(position)
    }
}