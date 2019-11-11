package de.taz.app.android.api.models

import de.taz.app.android.api.dto.ArticleDto
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.CacheableDownload
import de.taz.app.android.api.interfaces.Shareable
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.util.FileHelper
import java.io.File

data class Article(
    val articleHtml: FileEntry,
    val title: String?,
    val teaser: String?,
    val onlineLink: String?,
    val audioFile: FileEntry?,
    val pageNameList: List<String> = emptyList(),
    val imageList: List<FileEntry> = emptyList(),
    val authorList: List<Author> = emptyList(),
    val bookmarked: Boolean = false,
    val position: Int = 0,
    val percentage: Int = 0
): ArticleOperations, CacheableDownload, WebViewDisplayable, Shareable {
    constructor(articleDto: ArticleDto) : this(
        articleDto.articleHtml,
        articleDto.title,
        articleDto.teaser,
        articleDto.onlineLink,
        articleDto.audioFile,
        articleDto.pageNameList ?: emptyList(),
        articleDto.imageList ?: emptyList(),
        articleDto.authorList ?: emptyList()
    )

    override val articleFileName
        get() = articleHtml.name

    override fun getAllFiles(): List<FileEntry> {
        val list = mutableListOf(articleHtml)
        audioFile?.let { list.add(audioFile) }
        list.addAll(authorList.mapNotNull { it.imageAuthor })
        list.addAll(imageList)
        return list
    }

    override fun getFile(): File? {
        return getSection()?.let { section ->
            return FileHelper.getInstance()
                .getFile("${section.issueStub.tag}/$articleFileName")
        }
    }

    override fun previous(): Article? {
        return previousArticle()
    }

    override fun next(): Article? {
        return nextArticle()
    }

    override fun getLink(): String? {
        return onlineLink
    }
}
