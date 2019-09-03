package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ResourceInfo")
data class ResourceInfoWithoutFiles(
    @PrimaryKey val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String
)
