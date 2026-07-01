package de.taz.app.android.api.models

import android.content.Context
import androidx.room.Embedded
import androidx.room.Relation
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.persistence.repository.FileEntryRepository
import java.util.Date

data class Page(
    @Embedded
    val pageStub: PageStub,

    @Relation(
        parentColumn = "pdfFileName",
        entityColumn = "name"
    )
    val pdfFile: FileEntry,

    @Relation(
        entity = AudioStub::class,
        parentColumn = "podcastFileName",
        entityColumn = "fileName"
    )
    val audioFile: AudioWithFile?
) : DownloadableCollection {

    val pdfFileName: String get() = pageStub.pdfFileName
    val title: String? get() = pageStub.title
    val pagina: String? get() = pageStub.pagina
    val type: PageType? get() = pageStub.type
    val frameList: List<Frame>? get() = pageStub.frameList
    val baseUrl: String get() = pageStub.baseUrl
    val podcastFileName: String? get() = pageStub.podcastFileName
    val adIdList: List<String>? get() = pageStub.adIdList

    // Compatibility properties for existing code
    val pagePdf: FileEntry get() = pdfFile!!
    val podcast: Audio? get() = audioFile?.let {
        it.fileEntry?.let { file ->
            Audio(
                file,
                it.audioStub.playtime,
                it.audioStub.duration,
                it.audioStub.speaker,
                it.audioStub.breaks,
            )
        }
    }

    override val dateDownload: Date?
        get() = pdfFile.dateDownload

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return dateDownload
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        FileEntryRepository.getInstance(applicationContext).setDownloadDate(pdfFile, date)
    }

    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        return listOfNotNull(pdfFile)
    }

    override fun getDownloadTag(): String {
        return "page/$pdfFileName"
    }
}

enum class PageType {
    left,
    right,
    panorama
}
