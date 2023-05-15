package de.taz.app.android.audioPlayer

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.taz.app.android.util.Log

/**
 * Android Service that is started (in foreground) to allow the App to continue playing when it is
 * minimized and to be controlled via the Android Notifications.
 * see https://developer.android.com/guide/topics/media/media3/getting-started/playing-in-background
 */
class ArticleAudioMediaSessionService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val mediaSessionCallback = ArticleAudioMediaSessionCallback()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).setCallback(mediaSessionCallback).build()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // Return a MediaSession to link with the MediaController that is making this request.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

}

private class ArticleAudioMediaSessionCallback : MediaSession.Callback {
    private val log by Log

    /**
     * As the MediaSessionService may be triggered from external apps, it does strip the `localConfiguration`
     * that is containing the URI if set with MediaItem.fromUri(). Thus we have to store the Uri in the
     * `requestMetadata` and build the final `localConfiguration` based upon that.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val mediaItemsWithLocalUriInfo = mediaItems.mapNotNull {
            val mediaUri = it.requestMetadata.mediaUri
            if (mediaUri != null) {
                it.buildUpon().setUri(mediaUri).build()
            } else {
                log.error("Requests to add media item without a valid mediaUri in requestMetadata: $it")
                null
            }
        }

        return Futures.immediateFuture(mediaItemsWithLocalUriInfo)
    }
}

/**
 * Set the [RequestMetadata] on the [MediaItem] so that it might be prepared via [ArticleAudioMediaSessionCallback].
 */
fun MediaItem.Builder.setArticleAudioRequestMetadata(articleAudio: ArticleAudio): MediaItem.Builder =
    apply {
        setRequestMetadata(
            MediaItem.RequestMetadata.Builder().setMediaUri(articleAudio.audioFileUrl).build()
        )
    }
