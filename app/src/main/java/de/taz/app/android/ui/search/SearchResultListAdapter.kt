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
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.singletons.DateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class SearchResultListAdapter(
    private var searchResults: SearchResults,
    private val onBookmarkClick: (String, Date?) -> Unit,
    private val getBookmarkStateFlow: (String) -> Flow<Boolean>,
    private val onSearchResultClick: (Int) -> Unit,
) :
    RecyclerView.Adapter<SearchResultListAdapter.SearchResultListViewHolder>() {

    fun updateSearchResults(newSearchResults: SearchResults) {
        if (searchResults.sessionId == newSearchResults.sessionId) {
            val oldSize = searchResults.results.size
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

    override fun getItemCount() = searchResults.results.size

    class SearchResultListViewHolder(
        val view: View,
        private val getBookmarkStateFlow: (String) -> Flow<Boolean>,
        private val onSearchResultClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(view), CoroutineScope {

        override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main

        private var searchResultItem: ConstraintLayout = view.findViewById(R.id.search_result_item)
        var titleTextView: TextView = view.findViewById(R.id.search_result_title)
        var authorTextView: TextView = view.findViewById(R.id.search_result_author)
        var snippetTextView: TextView = view.findViewById(R.id.search_result_snippet)
        var dateTextView: TextView = view.findViewById(R.id.search_result_date)
        var sectionTextView: TextView = view.findViewById(R.id.search_result_section)
        var bookmarkIcon: ImageView = view.findViewById(R.id.search_result_bookmark_item)

        fun bind(position: Int, searchHit: SearchHit) {
            searchResultItem.setOnClickListener {
                onSearchResultClick(position)
            }
            launch {
                getBookmarkStateFlow(searchHit.articleFileName).collect {isBookmarked ->
                    if (isBookmarked) {
                        bookmarkIcon.setImageResource(R.drawable.ic_bookmark_filled)
                    } else {
                        bookmarkIcon.setImageResource(R.drawable.ic_bookmark)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchResultListViewHolder {
        val searchResultItem = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_search_result_item, parent, false)
        return SearchResultListViewHolder(searchResultItem, getBookmarkStateFlow, onSearchResultClick)
    }

    override fun onBindViewHolder(
        holder: SearchResultListViewHolder,
        position: Int
    ) {
        holder.coroutineContext.cancelChildren()

        val searchResultItem = searchResults.results[position]
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
        val dateString = if (BuildConfig.IS_LMD) {
            "Ausgabe ${ date?.let { DateHelper.dateToLocalizedMonthAndYearString(it) }}"
        } else {
            date?.let { DateHelper.dateToMediumLocalizedString(it) } ?: ""
        }

        // get the author(s) from the article
        val authorList = searchResultItem.authorList.map { it.name }
        if (authorList.isNotEmpty()) {
            val authorString = authorList.joinToString(", ")
            holder.authorTextView.apply {
                visibility = View.VISIBLE
                text = authorString
            }
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

        if (BuildConfig.IS_LMD) {
            holder.sectionTextView.text = ""
        }
        else {
            holder.sectionTextView.text = searchResultItem.sectionTitle
        }
        holder.bookmarkIcon.setOnClickListener {
            // We can assume that we want to bookmark it as we cannot de-bookmark a not downloaded article
            holder.bookmarkIcon.setImageResource(R.drawable.ic_bookmark_filled)
            onBookmarkClick(
                searchResultItem.articleFileName,
                DateHelper.stringToDate(searchResultItem.date)
            )
        }

        holder.bind(position, searchResultItem)
    }

    override fun onViewRecycled(holder: SearchResultListViewHolder) {
        holder.coroutineContext.cancelChildren()
    }


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