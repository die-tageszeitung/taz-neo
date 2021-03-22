package de.taz.app.android.api.models

import android.content.Context
import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.persistence.repository.PageRepository

import java.util.*
import java.util.Collections.max

data class IssueWithPages(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    override val minResourceVersion: Int,
    val imprint: Article?,
    override val isWeekend: Boolean,
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList(),
    override val moTime: String,
    override val dateDownload: Date?,
    override val lastDisplayableName: String?
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
        null,
        null
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
        val pagesDownloadedDate = pageList.map {
            it.pagePdf.dateDownload ?: it.getDownloadDate(context)
        }
        return if (pagesDownloadedDate.contains(null)) {
            null
        } else {
            super.getDownloadDate(context)
        }
    }

    override fun setDownloadDate(date: Date?, context: Context?) {
        pageList.forEach {
            it.setDownloadDate(date, context)
        }
        super.setDownloadDate(date, context)
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