package de.taz.app.android.ui.search

import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.singletons.DateHelper
import java.text.SimpleDateFormat
import java.util.*

class SearchResultListAdapter(
    private var searchResultList: List<SearchHitDto>
) :
    RecyclerView.Adapter<SearchResultListAdapter.SearchResultListViewHolder>() {

    class SearchResultListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var titleTextView: TextView = view.findViewById(R.id.search_result_title)

        var authorTextView: TextView = view.findViewById(R.id.search_result_author)
        var snippetTextView: TextView = view.findViewById(R.id.search_result_snippet)
        var dateTextView: TextView = view.findViewById(R.id.search_result_date)
        var sectionTextView: TextView = view.findViewById(R.id.search_result_section)
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

        // get the snippet to show in a TextView:
        val snippetWithParsableHtml =
            searchResultItem.snippet?.replace("<span class=\"snippet\">", "<font color='#d50d2e'>")
                ?.replace("</span>", "</font>")
        val snippet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(snippetWithParsableHtml, Html.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(snippetWithParsableHtml)
        }
        // Parse the date correctly, as it is given as a string but needs to be shown in different way
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).parse(searchResultItem.date)
        val dateString = date?.let { DateHelper.dateToMediumLocalizedString(it) } ?: ""

        // get the author(s) from the article
        val authorList = searchResultItem.article?.authorList?.map { it.name }
        if (authorList?.isNotEmpty() == true) {
            val authorString = authorList.toString()
                .replace("[", "")
                .replace("]", "")
            holder.authorTextView.text = authorString
        } else {
            holder.authorTextView.visibility = View.GONE
        }

        holder.titleTextView.text = searchResultItem.title
        holder.snippetTextView.text = snippet
        holder.dateTextView.text = dateString
        holder.sectionTextView.text = searchResultItem.sectionTitle
    }

    override fun getItemCount() = searchResultList.size
}