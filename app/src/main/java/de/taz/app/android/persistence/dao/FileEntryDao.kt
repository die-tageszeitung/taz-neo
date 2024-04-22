package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import java.util.Date

@Dao
interface FileEntryDao : BaseDao<FileEntry> {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    suspend fun getByName(name: String): FileEntry?

    @Query("SELECT dateDownload FROM FileEntry WHERE name == :name")
    suspend fun getDownloadDate(name: String): Date?

    @Query("SELECT * FROM FileEntry WHERE storageLocation = :storageLocation AND dateDownload IS NOT NULL")
    suspend fun getDownloadedByStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation NOT IN (:storageLocations)")
    suspend fun getExceptStorageLocation(storageLocations: List<StorageLocation>): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation != :storageLocation AND dateDownload IS NOT NULL")
    suspend fun getDownloadedExceptStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("DELETE FROM FileEntry WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM FileEntry WHERE name in (:fileNames)")
    suspend fun deleteList(fileNames: List<String>)

    @Query("""
        SELECT FileEntry.* FROM FileEntry 
         WHERE NOT EXISTS ( SELECT 1 FROM Image WHERE Image.fileEntryName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM Section WHERE Section.sectionFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM Article WHERE Article.articleFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM ArticleAuthor WHERE ArticleAuthor.authorFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM Audio WHERE Audio.fileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM Page WHERE Page.pdfFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM MomentFilesJoin WHERE MomentFilesJoin.momentFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM MomentImageJoin WHERE MomentImageJoin.momentFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM MomentCreditJoin WHERE MomentCreditJoin.momentFileName = FileEntry.name )
           AND NOT EXISTS ( SELECT 1 FROM ResourceInfoFileEntryJoin WHERE ResourceInfoFileEntryJoin.fileEntryName = FileEntry.name )
    """)
    suspend fun getOrphanedFileEntries(): List<FileEntry>
}