package de.taz.app.android.ui.search

import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
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
        val highLightColor = ResourcesCompat.getColor(
            holder.itemView.resources,
            R.color.text_highlight_mark_color,
            null
        )

        val toHighLightEntry =
            searchResultItem.snippet?.extractAllSubstrings("<span class=\"snippet\">", "</span>")
                ?: emptyList()
        val toHighLightTitle =
            searchResultItem.title.extractAllSubstrings("<span class=\"snippet\">", "</span>")

        // get the snippet  without HTML tags:
        val snippet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(searchResultItem.snippet, Html.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(searchResultItem.snippet)
        }

        // get the snippet  without HTML tags:
        val title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(searchResultItem.title, Html.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(searchResultItem.title)
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

        holder.titleTextView.text = title
        toHighLightTitle.map {
            setHighLightedText(holder.titleTextView, it, highLightColor)
        }
        holder.snippetTextView.text = snippet
        holder.snippetTextView.text = snippet
        toHighLightEntry.map { text ->
            setHighLightedText(holder.snippetTextView, text, highLightColor)
        }
        holder.dateTextView.text = dateString
        holder.sectionTextView.text = searchResultItem.sectionTitle
    }

    override fun getItemCount() = searchResultList.size

    /**
     * Use this method to highlight a text in TextView with a given color.
     *
     * @param textView        TextView or Edittext or Button (or derived from TextView)
     * @param textToHighlight String to highlight
     * @param color           Integer representing a color (eg with [ResourcesCompat.getColor])
     */
    private fun setHighLightedText(textView: TextView, textToHighlight: String, color: Int) {
        val textViewText = textView.text.toString()
        var index = textViewText.indexOf(textToHighlight, 0)
        val wordToSpan = SpannableString(textView.text)
        var startIndex = 0
        while (startIndex < textViewText.length && index != -1) {
            index = textViewText.indexOf(textToHighlight, startIndex)
            if (index == -1) break else {
                // set color here
                wordToSpan.setSpan(
                    BackgroundColorSpan(
                        color
                    ),
                    index,
                    index + textToHighlight.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.setText(wordToSpan, TextView.BufferType.SPANNABLE)
            }
            startIndex = index + textToHighlight.length
        }
    }

    /**
     * Use this method to extract all character between [fromString] and [toString].
     *
     * eg the String this function is called upon is:
     * "The <span class="snippet">image</span> of many <span class="snippet">images</span>."
     * would return ["image". "images"]
     *
     * @param fromString String which is the first delimiter
     * @param toString   String which is the last delimiter
     * @return List of the sub strings
     */
    private fun String.extractAllSubstrings(fromString: String, toString: String): List<String>{
        val resultList = mutableListOf<String>()

        var lastIndex = indexOf(fromString, 0)
        var sub: String
        while (lastIndex >= 0) {
            sub = substring(lastIndex)
            resultList.add(
                sub.substringAfter(fromString ).substringBefore(toString)
            )
            // Find the next occurrence if any
            lastIndex = sub.indexOf(
                fromString,
                lastIndex + 29
            )
        }
        return resultList
    }
}