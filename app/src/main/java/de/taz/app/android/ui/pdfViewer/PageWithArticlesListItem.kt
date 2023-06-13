package de.taz.app.android.ui.pdfViewer

import de.taz.app.android.api.models.Article

sealed class PageWithArticlesListItem {
    class Page(val page: PageWithArticles) : PageWithArticlesListItem()
    class Imprint(val imprint: Article) : PageWithArticlesListItem()
}