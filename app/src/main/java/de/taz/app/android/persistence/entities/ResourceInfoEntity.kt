package de.taz.app.android.persistence.entities

import androidx.room.*

@Entity(tableName = "ResourceInfo")
class ResourceInfoEntity(
    @PrimaryKey val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String
)
