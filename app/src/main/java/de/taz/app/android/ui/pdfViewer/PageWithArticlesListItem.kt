package de.taz.app.android.ui.pdfViewer

import de.taz.app.android.api.interfaces.ArticleOperations

sealed class PageWithArticlesListItem {
    class Page(val page: PageWithArticles) : PageWithArticlesListItem()
    class Imprint(val imprint: ArticleOperations) : PageWithArticlesListItem()
}