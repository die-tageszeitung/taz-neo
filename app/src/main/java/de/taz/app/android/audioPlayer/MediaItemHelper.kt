package de.taz.app.android.audioPlayer

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import java.io.File

private const val DISCLAIMER_MEDIA_ID = "disclaimer"
private const val DISCLAIMER_NOTE_FEMALE_ASSET_PATH = "/femaleNote.mp3"
private const val DISCLAIMER_NOTE_MALE_ASSET_PATH = "/maleNote.mp3"

/**
 * Helper classes used to connect [AudioPlayerItem] with the [MediaItem] that is used by the Android media framework.
 *
 * This class provides helpers to
 *   - create [MediaItem]s that correspond to our in-app [UiState] via its [UiStateHelper]
 *   - prepare the [MediaMetadata] with an URI to the mp3 so that the [ArticleAudioMediaSessionCallback] can pick it up for playing
 *   - map [MediaItem]s returned by [Player] events to [AudioPlayerItem]
 */
class MediaItemHelper(private val uiStateHelper: UiStateHelper) {

    companion object {
        fun List<AudioPlayerItem>.containsMediaItem(mediaItem: MediaItem): Boolean =
            indexOfMediaItem(mediaItem) >= 0

        fun List<AudioPlayerItem>.indexOfMediaItem(mediaItem: MediaItem): Int =
            indexOfFirst { item ->
                item.id == mediaItem.mediaId
            }

        fun MediaItem.belongsTo(audioPlayerItem: AudioPlayerItem): Boolean =
            mediaId == audioPlayerItem.id
    }
    private val storageService = StorageService.getInstance(uiStateHelper.applicationContext)
    private val log by Log

    fun getMediaItem(audioPlayerItem: AudioPlayerItem): MediaItem {
        val localUriString = storageService.getFileUri(audioPlayerItem.audio.file)
        val audioUri = "${audioPlayerItem.baseUrl}/${audioPlayerItem.audio.file.name}".toUri()
        val artworkData = getArtworkData(audioPlayerItem.uiItem)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(audioPlayerItem.uiItem.title)
            .setDisplayTitle(audioPlayerItem.uiItem.title)
            .setArtist(audioPlayerItem.uiItem.author)
            .setSubtitle(audioPlayerItem.uiItem.author)
            .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .build()

        return MediaItem.Builder()
            .setMediaId(audioPlayerItem.id)
            .setArticleAudioRequestMetadata(localUriString?.toUri() ?: audioUri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    private fun getArtworkData(uiItem: AudioPlayerItem.UiItem): ByteArray? {
        val path = if (uiItem.coverImageUri?.scheme == "file") {
            uiItem.coverImageUri.path
        } else {
            uiItem.coverImageGlidePath
        }

        return path?.let {
            try {
                val file = File(it)
                if (file.exists()) {
                    file.readBytes()
                } else {
                    null
                }
            } catch (e: Exception) {
                log.error("Failed to read artwork data from $it", e)
                null
            }
        }
    }

    /**
     * Set the [RequestMetadata] on the [MediaItem] so that it might be prepared via [ArticleAudioMediaSessionCallback].
     */
    private fun MediaItem.Builder.setArticleAudioRequestMetadata(audioFileUri: Uri): MediaItem.Builder =
        apply {
            setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(audioFileUri).build()
            )
        }


    fun createDisclaimerMediaItem(useMaleSpeaker: Boolean): MediaItem {
        val uiStateItem = uiStateHelper.getDisclaimerUiItem()

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(uiStateItem.title)
            .setDisplayTitle(uiStateItem.title)
            .setArtist(uiStateItem.author)
            .setSubtitle(uiStateItem.author)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()

        val disclaimerUri = if (useMaleSpeaker) {
            Uri.parse("asset://$DISCLAIMER_NOTE_MALE_ASSET_PATH")
        } else {
            Uri.parse("asset://$DISCLAIMER_NOTE_FEMALE_ASSET_PATH")
        }

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(disclaimerUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(DISCLAIMER_MEDIA_ID)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    fun isDisclaimer(mediaItem: MediaItem): Boolean {
        return mediaItem.mediaId == DISCLAIMER_MEDIA_ID
    }
}
