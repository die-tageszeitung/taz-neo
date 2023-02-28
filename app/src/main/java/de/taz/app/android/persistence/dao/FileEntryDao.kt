package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import java.util.*

@Dao
interface FileEntryDao : BaseDao<FileEntry> {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    suspend fun getByName(name: String): FileEntry?

    @Query("SELECT dateDownload FROM FileEntry WHERE name == :name")
    suspend fun getDownloadDate(name: String): Date?

    @Query("SELECT * FROM FileEntry WHERE dateDownload IS NOT NULL")
    suspend fun getDownloaded(): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation = :storageLocation AND dateDownload IS NOT NULL")
    suspend fun getDownloadedByStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation NOT IN (:storageLocations)")
    suspend fun getExceptStorageLocation(storageLocations: List<StorageLocation>): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation != :storageLocation AND dateDownload IS NOT NULL")
    suspend fun getDownloadedExceptStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE name IN (:names)")
    suspend fun getByNames(names: List<String>): List<FileEntry>

    @Query("DELETE FROM FileEntry WHERE name in (:fileNames)")
    suspend fun deleteList(fileNames: List<String>)
}