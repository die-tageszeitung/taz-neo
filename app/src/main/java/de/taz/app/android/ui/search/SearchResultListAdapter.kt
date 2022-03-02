package de.taz.app.android.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.dto.SearchHitDto

class SearchResultListAdapter(
    private var searchResultList: List<SearchHitDto>
) :
    RecyclerView.Adapter<SearchResultListAdapter.SearchResultListViewHolder>() {

    class SearchResultListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var titleTextView: TextView = view.findViewById(R.id.search_result_title)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchResultListViewHolder {
        val searchResultItem = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_search_result_item, parent, false)
        return SearchResultListViewHolder(searchResultItem)
    }

    override fun onBindViewHolder(
        holder: SearchResultListViewHolder,
        position: Int
    ) {
        val searchResultItem = searchResultList[position]
        holder.titleTextView.text = searchResultItem.title
    }

    override fun getItemCount() = searchResultList.size
}