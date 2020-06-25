package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.PageStub

@Dao
abstract class PageDao : BaseDao<PageStub>() {
    @Query("SELECT * FROM Page WHERE Page.pdfFileName == :fileName LIMIT 1")
    abstract fun get(fileName: String): PageStub?

    @Query("SELECT EXISTS (SELECT * FROM Page WHERE pdfFileName == :fileName AND downloadedStatus == 'done')")
    abstract fun isDownloadedLiveData(fileName: String): LiveData<Boolean>
}
