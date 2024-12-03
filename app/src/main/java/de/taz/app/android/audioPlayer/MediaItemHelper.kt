package de.taz.app.android.audioPlayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

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

    fun getMediaItem(audioPlayerItem: AudioPlayerItem): MediaItem {
        val audioUri = Uri.parse("${audioPlayerItem.baseUrl}/${audioPlayerItem.audio.file.name}")
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(audioPlayerItem.uiItem.title)
            .setArtist(audioPlayerItem.uiItem.author)
            // FIXME (johannes): passing a local file:// url for the artwork is not working 100%
            //     due to Androids App filesystem restrictions: Image file are stored in the private app
            //     storage and won't be accessible by other app. We could circumvent this by
            //     generating and passing a bitmap.
            //     Somehow we do get some errors on the logs, but the image is sometimes still shown.
            //     Thus we keep the Uri logic for now.
            .setArtworkUri(audioPlayerItem.uiItem.coverImageUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(audioPlayerItem.id)
            .setArticleAudioRequestMetadata(audioUri)
            .setMediaMetadata(mediaMetadata)
            .build()
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
            .setArtist(uiStateItem.author)
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
