package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.persistence.repository.IssueRepository


import java.util.*

data class IssueWithPages(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    override val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    val imprint: Article?,
    override val isWeekend: Boolean,
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList(),
    override val moTime: String,
    override val dateDownload: Date?,
    override val dateDownloadWithPages: Date?,
    override val lastDisplayableName: String?,
    override val lastPagePosition: Int?
) : IssueOperations, DownloadableCollection, ObservableDownload {

    constructor(issue: Issue) : this(
        issue.feedName,
        issue.date,
        issue.moment,
        issue.key,
        issue.baseUrl,
        issue.status,
        issue.minResourceVersion,
        issue.imprint,
        issue.isWeekend,
        issue.sectionList,
        issue.pageList,
        issue.moTime,
        issue.dateDownload,
        issue.dateDownloadWithPages,
        issue.lastDisplayableName,
        issue.lastPagePosition
    )

    override fun getAllFiles(): List<FileEntry> {
        val files = mutableListOf<List<FileEntry>>()
        imprint?.let {
            files.add(imprint.getAllFiles())
        }
        sectionList.forEach { section ->
            files.add(section.getAllFiles())
            getArticleList().forEach { article ->
                files.add(article.getAllFiles())
            }
        }
        pageList.forEach { page ->
            files.add(page.getAllFiles())
        }
        return files.flatten().distinct()
    }

    override fun getAllFileNames(): List<String> {
        return getAllFiles().map { it.name }
    }

    override fun getDownloadDate(context: Context?): Date? {
        return IssueRepository.getInstance(context).getDownloadDate(this)
    }

    override fun setDownloadDate(date: Date?, context: Context?) {
        IssueRepository.getInstance(context).apply {
            setDownloadDate(this@IssueWithPages, date)
            get(issueKey)?.let {
                // downloading an issue with pages also means downloading the regular issue
                IssueRepository.getInstance(context).setDownloadDate(it, date)
            }
        }
    }

    override fun getDownloadTag(): String {
        return "$tag/pdf"
    }

    private fun getArticleList(): List<Article> {
        val articleList = mutableListOf<Article>()
        sectionList.forEach {
            articleList.addAll(it.articleList)
        }
        return articleList
    }
}