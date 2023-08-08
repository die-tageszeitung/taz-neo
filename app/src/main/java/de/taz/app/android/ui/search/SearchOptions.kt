package de.taz.app.android.ui.search

import de.taz.app.android.api.models.Sorting
import de.taz.app.android.api.variables.SearchFilter

data class SearchOptions(
    val searchText: String,
    val advancedOptions: AdvancedSearchOptions
) {
    fun isValid(): Boolean {
        return searchText.isNotBlank() || !advancedOptions.author.isNullOrBlank() || !advancedOptions.title.isNullOrBlank()
    }
}

data class AdvancedSearchOptions(
    val title: String? = null,
    val author: String? = null,
    val searchFilter: SearchFilter = SearchFilter.all,
    val sorting: Sorting = Sorting.relevance,
    val publicationDateFilter: PublicationDateFilter = PublicationDateFilter.Any,
) {
    companion object {
        val Default = AdvancedSearchOptions()
    }

}