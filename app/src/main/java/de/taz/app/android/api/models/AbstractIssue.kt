package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.IssueOperations

interface AbstractIssue: IssueOperations {
    val imprint: Article?
    val sectionList: List<Section>

    fun getArticles(): List<Article> {
        return sectionList.flatMap { it.articleList }
    }
}