package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.persistence.repository.AudioRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.Log
import java.util.*

@Entity(
    tableName = "Section",
    foreignKeys = [
        ForeignKey(
            entity = AudioStub::class,
            parentColumns = ["fileName"],
            childColumns = ["podcastFileName"]
        )
    ],
    indices = [
        Index("podcastFileName")
    ]
)
data class SectionStub(
    @PrimaryKey val sectionFileName: String,
    override val issueDate: String,
    override val title: String,
    override val type: SectionType,
    override val extendedTitle: String?,
    override val dateDownload: Date?,
    val podcastFileName: String?,
) : SectionOperations {

    @Ignore
    override val key: String = sectionFileName
    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        val list: MutableList<FileEntry> = mutableListOf()
        FileEntryRepository.getInstance(applicationContext).get(sectionFileName)?.let {
            list.add(it)
        }
        val images =
            SectionRepository.getInstance(applicationContext).imagesForSectionStub(sectionFileName)
        list.addAll(images.map { FileEntry(it) })
        return list.distinct()
    }

    companion object {
        private val log by Log

        private fun getPodcastFileName(section: SectionOperations): String? {
            return if (section is Section) {
                section.podcast?.file?.name
            } else if (section is SectionStub) {
                section.podcastFileName
            } else {
                log.error("SectionOperations is neither Section nor SectionStub")
                null
            }
        }
    }

    constructor(section: SectionOperations): this(
        section.key,
        section.issueDate,
        section.title,
        section.type,
        section.extendedTitle,
        section.dateDownload,
        getPodcastFileName(section)
    )

    constructor(section: Section) : this(
        section.sectionHtml.name,
        section.issueDate,
        section.title,
        section.type,
        section.extendedTitle,
        section.dateDownload,
        section.podcast?.file?.name,
    )

    override fun getDownloadTag(): String {
        return sectionFileName
    }

    override suspend fun getPodcast(applicationContext: Context): Audio? {
        return podcastFileName?.let { AudioRepository.getInstance(applicationContext).get(it) }
    }

    override suspend fun getPodcastImage(applicationContext: Context): Image? {
        return SectionRepository.getInstance(applicationContext).firstImageForSectionStub(sectionFileName)
    }
}

