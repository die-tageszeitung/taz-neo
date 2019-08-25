package de.taz.app.android.persistence.entities

import androidx.room.*
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.persistence.AppDatabase

@Entity(tableName = "ResourceInfo")
class ResourceInfoEntity(
    @PrimaryKey val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String
) : GenericEntity<ResourceInfo>() {

    override fun toObject(): ResourceInfo {
        return ResourceInfo(resourceVersion, resourceBaseUrl, resourceZip, getFileEntries())
    }

    private fun getFileEntries(): List<FileEntry> {
        return appDatabase.resourceInfoFileEntryJoinDao().getFileEntriesForResourceInfo(resourceVersion).map { it.toObject() }
    }
}
