package de.taz.app.android.ui.search

sealed class SearchUiState {
    object Init : SearchUiState()
    object Loading : SearchUiState()
    object NoResults : SearchUiState()
    data class Results(
        val searchResults: SearchResults,
        val isLoadingMore: Boolean,
    ) : SearchUiState()
}