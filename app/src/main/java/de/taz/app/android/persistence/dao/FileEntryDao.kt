package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import java.util.*

@Dao
abstract class FileEntryDao : BaseDao<FileEntry>() {

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getByName(name: String): FileEntry?

    @Query("SELECT * FROM FileEntry WHERE name == :name")
    abstract fun getLiveDataByName(name: String): LiveData<FileEntry?>

    @Query("SELECT dateDownload FROM FileEntry WHERE name == :name")
    abstract fun getDownloadDate(name: String): Date?

    @Query("SELECT * FROM FileEntry WHERE dateDownload IS NOT NULL")
    abstract fun getDownloaded(): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation = :storageLocation")
    abstract fun getByStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation = :storageLocation AND dateDownload IS NOT NULL")
    abstract fun getDownloadedByStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation NOT IN (:storageLocations)")
    abstract fun getExceptStorageLocation(storageLocations: List<StorageLocation>): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE storageLocation != :storageLocation AND dateDownload IS NOT NULL")
    abstract fun getDownloadedExceptStorageLocation(storageLocation: StorageLocation): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE name IN (:names)")
    abstract fun getByNames(names: List<String>): List<FileEntry>

    @Query("SELECT * FROM FileEntry WHERE name LIKE :filterString")
    abstract fun filterByName(filterString: String): List<FileEntry>

    @Query("SELECT FileEntry.name FROM FileEntry WHERE name LIKE :filterString")
    abstract fun getNamesContaining(filterString: String): List<String>

    @Query("DELETE FROM FileEntry WHERE name in (:fileNames)")
    abstract fun deleteList(fileNames: List<String>)

    @Query(
        """
            SELECT FileEntry.* FROM FileEntry
            INNER JOIN Article ON Article.articleFileName = FileEntry.name
            WHERE FileEntry.name IN (:fileNames) AND NOT Article.bookmarked
        """
    )
    abstract fun filterFilesThatArePartOfBookmarkedArticle(
        fileNames: List<String>
    ): List<FileEntry>

    @Query(
        """
            SELECT FileEntry.* FROM FileEntry
            LEFT JOIN ArticleAuthor ON ArticleAuthor.authorFileName = FileEntry.name
            WHERE FileEntry.name IN (:fileNames) AND ArticleAuthor.id IS NULL
        """
    )
    abstract fun filterFilesThatBelongToAnAuthor(
        fileNames: List<String>
    ): List<FileEntry>


    @Query(
        """
            SELECT FileEntry.* FROM FileEntry
            LEFT JOIN ArticleAuthor ON ArticleAuthor.authorFileName = FileEntry.name
            INNER JOIN Article ON Article.articleFileName = FileEntry.name
            WHERE FileEntry.name IN (:fileNames) AND ArticleAuthor.id IS NULL AND NOT Article.bookmarked
        """
    )
    abstract fun filterFilesThatArePartOfBookmarkedArticleOrAuthor(
        fileNames: List<String>
    ): List<FileEntry>
}