package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

data class Issue(
    override val feedName: String,
    override val date: String,
    val moment: Moment,
    val key: String? = null,
    override val baseUrl: String,
    override val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val imprint: Article?,
    val fileList: List<String> = emptyList(),
    val fileListPdf: List<String> = emptyList(),
    val sectionList: List<Section> = emptyList(),
    val pageList: List<Page> = emptyList(),
    override val dateDownload: Date? = null
) : IssueOperations, CacheableDownload {

    constructor(feedName: String, issueDto: IssueDto) : this(
        feedName,
        issueDto.date,
        Moment(feedName, issueDto.date, issueDto.moment),
        issueDto.key,
        issueDto.baseUrl,
        issueDto.status,
        issueDto.minResourceVersion,
        issueDto.zipName,
        issueDto.zipPdfName,
        issueDto.navButton,
        issueDto.imprint?.let { Article(feedName, issueDto.date, it, ArticleType.IMPRINT) },
        issueDto.fileList,
        issueDto.fileListPdf ?: emptyList(),
        issueDto.sectionList?.map { Section(feedName, issueDto.date, it) } ?: emptyList(),
        issueDto.pageList?.map { Page(feedName, issueDto.date, it) } ?: emptyList()
    )

    private val log by Log

    override fun getAllFiles(): List<FileEntry> {
        val files = mutableListOf(moment.imageList)
        imprint?.let {
            files.add(imprint.getAllFiles())
        }
        files.addAll(sectionList.map { it.getAllFiles() })
        return files.flatten().distinct()
    }

    override fun getDownloadTag(): String? {
        return tag
    }

    suspend fun downloadMoment(applicationContext: Context) {
        withContext(Dispatchers.IO) {
            DownloadService.download(applicationContext, moment)
        }
    }

    suspend fun downloadPages(applicationContext: Context) {
        withContext(Dispatchers.IO) {
            pageList.forEach {
                DownloadService.download(applicationContext, it)
            }
        }
    }

    override fun getIssueOperations(): IssueOperations? {
        return this
    }

    fun getArticleList(): List<Article> {
        val articleList = mutableListOf<Article>()
        sectionList.forEach {
            articleList.addAll(it.articleList)
        }
        return articleList
    }

    fun deleteFiles() {
        log.debug("deleting issue $tag, downloaded on $dateDownload")
        val fileHelper = FileHelper.getInstance()
        val downloadRepository = DownloadRepository.getInstance()

        // set status for associated downloads to deleted


        // delete all associated file entries (without moment)
        // delete imprint
       imprint?.let { imprint ->
           log.debug("deleting imprint: ${imprint.articleFileName} for issue $tag")
           val download = downloadRepository.getOrThrow(imprint.articleFileName)
           downloadRepository.setStatus(download, DownloadStatus.deleted)
           log.debug("imprint filename to be deleted from filesystem: ${imprint.articleHtml.name}")
           fileHelper.deleteFileFromFileSystem("$tag/${imprint.articleHtml.name}")
        }

        // delete pages
        pageList.map { page ->
            val download = downloadRepository.getOrThrow(page.pagePdf.name)
            downloadRepository.setStatus(download, DownloadStatus.deleted)
            fileHelper.deleteFileFromFileSystem("$tag/${page.pagePdf.name}")
        }

        // delete sections
        sectionList.map { section ->
            val download = downloadRepository.getOrThrow(section.sectionHtml.name) //what's the difference betweeen sectionHtml.name and sectionFileName?
            downloadRepository.setStatus(download, DownloadStatus.deleted)
            fileHelper.deleteFileFromFileSystem("$tag/${section.sectionHtml.name}")
        }

        // delete articles
        getArticleList().map { article ->
            if (!article.bookmarked) {
                //delete audio files
                article.audioFile?.let { audioFile ->
                    val download = downloadRepository.getOrThrow(audioFile.name)
                    downloadRepository.setStatus(download, DownloadStatus.deleted)
                    fileHelper.deleteFileFromFileSystem("$tag/${audioFile.name}")
                }
                // delete authors
                // how to make sure they are not used somewhere else?

                // delete images
                article.imageList.map { image ->
                    val download = downloadRepository.getOrThrow(image.name)
                    downloadRepository.setStatus(download, DownloadStatus.deleted)
                    fileHelper.deleteFileFromFileSystem("$tag/${image.name}")

                }

                val download = downloadRepository.getOrThrow(article.articleFileName)
                downloadRepository.setStatus(download, DownloadStatus.deleted)
                fileHelper.deleteFileFromFileSystem("$tag/${article.articleFileName}")
            }
        }

        // set dateDownload = null
        IssueRepository.getInstance().resetDownloadDate(this)
    }

}

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}

enum class ArticleType {
    STANDARD, IMPRINT;
}