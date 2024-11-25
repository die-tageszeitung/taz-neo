package de.taz.app.android.audioPlayer

import android.net.Uri
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.persistence.repository.AbstractIssueKey

class AudioPlayerItem(
    val id: String,
    val audio: Audio,
    val baseUrl: String,
    val uiItem: UiItem,
    val issueKey: AbstractIssueKey?,
    val playableKey: String?,
    val searchHit: SearchHit? = null,
    val type: Type,
) {
    enum class Type {
        PODCAST,
        ARTICLE,
        SEARCH_HIT,
    }

    override fun toString(): String {
        return "AudioPlayerItem($id)[${audio.file}, ${uiItem.title}, ${issueKey?.date}/$playableKey]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioPlayerItem

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }


    /**
     * UI representation of the [AudioPlayerItem]
     */
    data class UiItem(
        val title: String,
        val author: String?,
        val coverImageUri: Uri?,
        val coverImageGlidePath: String?,
        val openItemSpec: OpenItemSpec?,
    ) {
        val hasCoverImage = coverImageUri != null || coverImageGlidePath != null
    }

}