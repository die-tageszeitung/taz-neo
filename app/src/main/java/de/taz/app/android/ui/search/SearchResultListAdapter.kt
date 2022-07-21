package de.taz.app.android.ui.search

import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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

    class SearchResultListViewHolder(
        val view: View
    ) : RecyclerView.ViewHolder(view) {
        private var searchResultItem: ConstraintLayout = view.findViewById(R.id.search_result_item)
        var titleTextView: TextView = view.findViewById(R.id.search_result_title)
        var authorTextView: TextView = view.findViewById(R.id.search_result_author)
        var snippetTextView: TextView = view.findViewById(R.id.search_result_snippet)
        var dateTextView: TextView = view.findViewById(R.id.search_result_date)
        var sectionTextView: TextView = view.findViewById(R.id.search_result_section)

        fun bind(position: Int) {
            searchResultItem.setOnClickListener {
                val fragment = SearchResultPagerFragment.instance(position)
                val activity: AppCompatActivity = view.context as AppCompatActivity
                activity.supportFragmentManager.beginTransaction()
                    .add(
                        android.R.id.content,
                        fragment
                    )
                    .addToBackStack(null)
                    .commit()
            }
        }
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

        holder.bind(position)
    }

    override fun getItemCount() = searchResultList.size

    // region helper functions

    /**
     * Highlight a [textToHighlight] in a given [textView] with a given [markColor].
     *
     * @param textView        TextView or Edittext or Button (or derived from TextView)
     * @param textToHighlight String to highlight
     * @param markColor           Integer representing a color (eg with [ResourcesCompat.getColor])
     */
    private fun setHighLightedText(textView: TextView, textToHighlight: String, markColor: Int) {
        val textColor = ResourcesCompat.getColor(
            textView.resources,
            R.color.text_highlight_text_color,
            null
        )
        val textViewText = textView.text.toString()
        val wordToSpan = SpannableString(textView.text)
        var currentIndex = 0
        while (currentIndex < textViewText.length) {
            val index = textViewText.indexOf(textToHighlight, currentIndex)
            if (index == -1) break else {
                // set mark color here
                wordToSpan.setSpan(
                    BackgroundColorSpan(
                        markColor
                    ),
                    index,
                    index + textToHighlight.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // set text color here
                wordToSpan.setSpan(
                    ForegroundColorSpan(
                        textColor
                    ),
                    index,
                    index + textToHighlight.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.setText(wordToSpan, TextView.BufferType.SPANNABLE)
            }
            currentIndex = index + textToHighlight.length
        }
    }

    /**
     * Extract all characters between [fromString] and [toString].
     *
     * eg the String this function is called upon is:
     * "The <span class="snippet">image</span> of many <span class="snippet">images</span>."
     * would return ["image", "images"]
     *
     * @param fromString String which is the first delimiter
     * @param toString   String which is the last delimiter
     * @return List of the sub strings
     */
    private fun String.extractAllSubstrings(fromString: String, toString: String): List<String> {
        val resultList = mutableListOf<String>()

        var lastIndex = indexOf(fromString, 0)
        var sub: String
        while (lastIndex >= 0) {
            sub = substring(lastIndex)
            resultList.add(
                sub.substringAfter(fromString).substringBefore(toString)
            )
            // Find the next occurrence if any
            lastIndex = sub.indexOf(
                fromString,
                lastIndex + fromString.length + toString.length
            )
        }
        return resultList
    }

    // endregion
}