package de.taz.app.android.api.models

import androidx.room.*

@Entity(tableName = "ResourceInfo")
class ResourceInfoWithoutFiles(
    @PrimaryKey val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String
)
