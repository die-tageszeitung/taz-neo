package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "PodcastEpisodes",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("podcastId"), Index("id")]
)
data class PodcastEpisodeEntity(
    @PrimaryKey val id: Int,
    val podcastId: Int,
    val pubTime: Date,
    val mediaSyncId: Int,
    val title: String?,
    val teaser: String?,
    val headLine: String?,
    val authors: String?,
    val iconFileEntryName: String?,
    val audioFileName: String?,
    val transcriptionFileName: String?,
    val playtime: Int?,
    val duration: Float?,
    val alreadyPlayed: Float = 0f
)
