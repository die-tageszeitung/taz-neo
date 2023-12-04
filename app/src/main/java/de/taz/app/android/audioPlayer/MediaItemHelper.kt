package de.taz.app.android.audioPlayer

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.singletons.StoragePathService

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
class MediaItemHelper(applicationContext: Context, private val uiStateHelper: UiStateHelper) {
    private val storagePathService = StoragePathService.getInstance(applicationContext)

    fun containsMediaItem(audioPlayerItem: AudioPlayerItem, mediaItem: MediaItem): Boolean {
        return when (audioPlayerItem) {
            is ArticleAudio -> audioPlayerItem.article.key == mediaItem.mediaId
            is IssueAudio -> audioPlayerItem.indexOf(mediaItem) >= 0
            is PodcastAudio -> audioPlayerItem.audio.file.name == mediaItem.mediaId
        }
    }

    fun copyWithCurrentMediaItem(
        audioPlayerItem: AudioPlayerItem,
        mediaItem: MediaItem
    ): AudioPlayerItem {
        return when (audioPlayerItem) {
            is ArticleAudio -> {
                check(containsMediaItem(audioPlayerItem, mediaItem))
                audioPlayerItem
            }

            is IssueAudio -> {
                val index = audioPlayerItem.indexOf(mediaItem)
                check(index >= 0)
                return audioPlayerItem.copy(currentIndex = index)
            }

            is PodcastAudio -> {
                check(containsMediaItem(audioPlayerItem, mediaItem))
                audioPlayerItem
            }
        }
    }

    suspend fun getMediaItems(audioPlayerItem: AudioPlayerItem): List<MediaItem> {
        return when (audioPlayerItem) {
            is ArticleAudio -> {
                val audioUri = createAudioFileUri(
                    audioPlayerItem.issueStub,
                    requireNotNull(audioPlayerItem.article.audio)
                )
                val mediaItem = createArticleMediaItem(audioPlayerItem.article, audioUri)
                listOf(mediaItem)
            }

            is IssueAudio -> {
                audioPlayerItem.articles.mapNotNull { article ->
                    if (article.audio == null) {
                        // IssueAudio must only contain articles with an audioFile, but the type system
                        // can't know it, so we have this additional unnecessary null check to prevent warnings.
                        return@mapNotNull null
                    }

                    val audioUri = createAudioFileUri(audioPlayerItem.issueStub, article.audio)
                    createArticleMediaItem(article, audioUri)
                }
            }

            is PodcastAudio -> {
                val audioUri = createAudioFileUri(audioPlayerItem.issueStub, audioPlayerItem.audio)
                val mediaItem = createPodcastMediaItem(audioPlayerItem, audioUri)
                listOf(mediaItem)
            }
        }
    }

    private suspend fun createAudioFileUri(issueStub: IssueStub, audio: Audio): Uri {
        val baseUrl = storagePathService.determineBaseUrl(audio.file, issueStub)
        return Uri.parse("$baseUrl/${audio.file.name}")
    }

    private fun createArticleMediaItem(article: Article, audioUri: Uri): MediaItem {
        val title = uiStateHelper.getAudioTitle(article)
        val authorText = uiStateHelper.getAudioAuthor(article)
        val imageUri = uiStateHelper.getAudioImage(article)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(authorText)
            // FIXME (johannes): passing a local file:// url for the artwork is not working 100%
            //     due to Androids App filesystem restrictions: Image file are stored in the private app
            //     storage and won't be accessible by other app. We could circumvent this by
            //     generating and passing a bitmap.
            //     Somehow we do get some errors on the logs, but the image is sometimes still shown.
            //     Thus we keep the Uri logic for now.
            .setArtworkUri(imageUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(article.key)
            .setArticleAudioRequestMetadata(audioUri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    private fun createPodcastMediaItem(podcastAudio: PodcastAudio, audioUri: Uri): MediaItem {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(uiStateHelper.getTitleForPodcast(podcastAudio))
            .setArtist(null)
            // We don't set the artwork here, as the podcast mp3 does contain a preview image which
            // is used as a fallback by the Android media3 framework.
            .setArtworkUri(null)
            .build()

        return MediaItem.Builder()
            .setMediaId(podcastAudio.audio.file.name)
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

/**
 * Get the index of the the [Article] corresponding to [MediaItem]
 * or -1 if it is not found.
 */
fun IssueAudio.indexOf(mediaItem: MediaItem): Int =
    articles.indexOfFirst { it.key == mediaItem.mediaId }