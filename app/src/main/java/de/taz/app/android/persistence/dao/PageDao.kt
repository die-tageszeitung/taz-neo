package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.PageStub
import java.util.*

@Dao
abstract class PageDao : BaseDao<PageStub>() {
    @Query("SELECT Page.* FROM Page WHERE Page.pdfFileName == :fileName LIMIT 1")
    abstract fun get(fileName: String): PageStub?

    @Query("SELECT Page.* FROM Page WHERE Page.pdfFileName == :fileName LIMIT 1")
    abstract fun getLiveData(fileName: String): LiveData<PageStub?>

    @Query("SELECT dateDownload FROM Page WHERE pdfFileName == :fileName")
    abstract fun getDownloadDate(fileName: String): Date?

    @Query("SELECT EXISTS (SELECT * FROM Page WHERE pdfFileName == :fileName AND dateDownload IS NOT NULL)")
    abstract fun isDownloadedLiveData(fileName: String): LiveData<Boolean>

    @Query("""
        DELETE FROM Page
        WHERE 
            pdfFileName in (:pageNames) AND
            (pdfFileName NOT IN (SELECT pdfFileName FROM IssuePageJoin))
    """)
    abstract fun deleteIfNoIssueRelated(pageNames: List<String>)
}
