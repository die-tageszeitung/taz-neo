package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.SectionOperations
import java.util.*

data class Section(
    val sectionHtml: FileEntry,
    override val issueDate: String,
    override val title: String,
    override val type: SectionType,
    val navButton: Image,
    val articleList: List<Article>,
    val imageList: List<Image>,
    override val extendedTitle: String?,
    override val dateDownload: Date?,
    val podcast: Audio?,
) : SectionOperations {

    override val key: String
        get() = sectionHtml.name

    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        val list = mutableListOf(sectionHtml)
        list.addAll(imageList.filter { it.resolution == ImageResolution.normal }.map { FileEntry(it) })
        return list.distinct()
    }

    override fun getDownloadTag(): String {
        return sectionHtml.name
    }

    override suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return super.getIssueStub(applicationContext)
    }

    override suspend fun getPodcast(applicationContext: Context): Audio? = podcast

    override suspend fun getPodcastImage(applicationContext: Context): Image? {
        return imageList.firstOrNull()
    }
}

