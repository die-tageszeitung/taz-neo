package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "Audio",
    foreignKeys = [
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["name"],
            childColumns = ["fileName"]
        )
    ]
)
data class AudioStub(
    @PrimaryKey val fileName: String,
    val playtime: Int?,
    val duration: Float?,
    val speaker: AudioSpeaker,
    val breaks: List<Float>?,
) {

    constructor(audio: Audio): this(
        audio.file.name,
        audio.playtime,
        audio.duration,
        audio.speaker,
        audio.breaks
    )
}

