package de.taz.app.android.ui.bookmarks

import de.taz.app.android.api.models.Article


sealed class BookmarkListItem {
    class Item(val bookmark: Article) : BookmarkListItem()
    class Header(
        val momentImageUri: String?,
        val localizedDateString: String,
        val dateString: String
    ) : BookmarkListItem()
}