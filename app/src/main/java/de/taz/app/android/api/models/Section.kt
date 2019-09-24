package de.taz.app.android.api.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import de.taz.app.android.api.dto.SectionDto
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.persistence.repository.DownloadRepository

data class Section(
    val sectionHtml: FileEntry,
    val title: String,
    val type: SectionType,
    val articleList: List<Article> = emptyList(),
    val imageList: List<FileEntry> = emptyList()
) {
    constructor(sectionDto: SectionDto) : this(
        sectionDto.sectionHtml,
        sectionDto.title,
        sectionDto.type,
        sectionDto.articleList?.map { Article(it) } ?: listOf(),
        sectionDto.imageList ?: listOf()
    )

    fun isDownloaded(): Boolean {
        return DownloadRepository.getInstance().isDownloaded(getAllFileNames())
    }

    fun isDownloadedLiveDate(): LiveData<Boolean> {
        return DownloadRepository.getInstance().isDownloadedLiveData(getAllFileNames())
    }

    private fun getAllFileNames(): List<String> {
        val list = mutableListOf(sectionHtml.name)
        list.addAll(imageList.map { image -> image.name })
        articleList.forEach{ article -> list.addAll(article.getAllFileEntries()) }
        return list
    }

}

