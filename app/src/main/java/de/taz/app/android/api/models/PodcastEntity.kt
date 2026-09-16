package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Podcasts",
    indices = [Index("id")]
)
data class PodcastEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val title: String,
    val displayName: String,
    val version: Int,
    val useDefaultIcon: Boolean,
    val defaultIconFileEntryName: String?,
    val episodeCnt: Int
)
