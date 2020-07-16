package de.taz.app.android.api.models

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository

data class Article(
    val articleHtml: FileEntry,
    val issueFeedName: String,
    val issueDate: String,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String>,
    val imageList: List<Image>,
    val authorList: List<Author>,
    override val articleType: ArticleType,
    val bookmarked: Boolean,
    val position: Int,
    val percentage: Int,
    override val downloadedStatus: DownloadStatus?
) : ArticleOperations {

    constructor(
        issueFeedName: String,
        issueDate: String,
        articleDto: ArticleDto,
        articleType: ArticleType = ArticleType.STANDARD
    ) : this(
        FileEntry(articleDto.articleHtml, "$issueFeedName/$issueDate"),
        issueFeedName,
        issueDate,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.audioFile?.let { FileEntry(it, "$issueFeedName/$issueDate") },
        articleDto.pageNameList ?: emptyList(),
        articleDto.imageList?.map { Image(it, "$issueFeedName/$issueDate") } ?: emptyList(),
        articleDto.authorList?.map { Author(it) } ?: emptyList(),
        articleType,
        false,
        0,
        0,
        DownloadStatus.pending
    )

    override val key: String
        get() = articleHtml.name

    override suspend fun getAllFiles(): List<FileEntryOperations> {
        val list = mutableListOf<FileEntryOperations>(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal })
        return list
    }

    override fun getAllFileNames(): List<String> {
        val list = mutableListOf<FileEntryOperations>(articleHtml)
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal })
        return list.map { it.name }.distinct()
    }

    override fun setDownloadStatus(downloadStatus: DownloadStatus) {
        ArticleRepository.getInstance()
            .update(ArticleStub(this).copy(downloadedStatus = downloadedStatus))
    }

    override fun getLiveData(applicationContext: Context?): LiveData<Article?> {
        return ArticleRepository.getInstance(applicationContext).getLiveData(key)
    }

}

enum class ArticleType {
    STANDARD, IMPRINT;
}
