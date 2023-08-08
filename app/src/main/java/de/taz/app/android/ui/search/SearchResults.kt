package de.taz.app.android.ui.search

import de.taz.app.android.api.models.SearchHit

data class SearchResults(
    val sessionId: String,
    val searchOptions: SearchOptions,
    val totalResults: Int,
    val results: List<SearchHit>
) {
    val loadedResults = results.size
}