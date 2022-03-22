package de.taz.app.android.ui.search

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.dto.SearchHitDto
import de.taz.app.android.ui.webview.AppWebView

class SearchResultPagerAdapter(
    var searchResultList: List<SearchHitDto>
) :
    RecyclerView.Adapter<SearchResultPagerAdapter.SearchResultPagerViewHolder>() {

    class SearchResultPagerViewHolder(
        val view: AppWebView
    ) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SearchResultPagerViewHolder {
        val textView = AppWebView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        return SearchResultPagerViewHolder(textView)
    }

    override fun onBindViewHolder(
        holder: SearchResultPagerViewHolder,
        position: Int
    ) {
        val searchResultItem = searchResultList[position]
        holder.view.loadData(searchResultItem.articleHtml!!, null, null)
    }

    override fun getItemCount() = searchResultList.size

    // region helper functions

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
                lastIndex + 29
            )
        }
        return resultList
    }

    // endregion
}