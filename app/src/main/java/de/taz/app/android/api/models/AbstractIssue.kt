package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.IssueOperations

interface AbstractIssue: IssueOperations {
    val imprint: Article?
    val sectionList: List<Section>
    val pageList: List<Page>

    fun getArticles(): List<Article> {
        return sectionList.flatMap { it.articleList }
    }
}