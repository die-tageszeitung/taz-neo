package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.taz.app.android.audioPlayer.AudioPlayerItem
import de.taz.app.android.audioPlayer.AudioPlayerItem.Type

@Entity(
    tableName = "Playlist",
    foreignKeys = [
        ForeignKey(
            entity = AudioStub::class,
            parentColumns = ["fileName"],
            childColumns = ["audioFileName"]
        )
    ],
    indices = [Index("audioFileName")]
)
data class AudioPlayerItemStub(
    @PrimaryKey val audioPlayerItemId: String,
    val audioFileName: String,
    val baseUrl: String,
    val uiTitle: String,
    val uiAuthor: String? = null,
    val uiCoverImageUri: String? = null,
    val uiCoverImageGlidePath: String? = null,
    val uiOpenItemSpecDisplayableKey: String? = null,
    val issueDate: String? = null,
    val issueFeedName: String? = null,
    val issueStatus: IssueStatus? = null,
    val playableKey: String? = null,
    val audioPlayerItemType: Type,
) {
    constructor(audioPlayerItem: AudioPlayerItem) : this(
        audioPlayerItem.id,
        audioPlayerItem.audio.file.name,
        audioPlayerItem.baseUrl,
        audioPlayerItem.uiItem.title,
        audioPlayerItem.uiItem.author,
        audioPlayerItem.uiItem.coverImageUri.toString(),
        audioPlayerItem.uiItem.coverImageGlidePath,
        audioPlayerItem.uiItem.openItemSpec.toString(),
        audioPlayerItem.issueKey?.date,
        audioPlayerItem.issueKey?.feedName,
        audioPlayerItem.issueKey?.status,
        audioPlayerItem.playableKey,
        audioPlayerItem.type
    )
}
