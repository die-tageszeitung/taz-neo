package de.taz.app.android.audioPlayer

import android.content.Context
import android.net.Uri
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.singletons.StorageService

/**
 * Helper classes to map [AudioPlayerItem]s to [UiState.Item]s and get [UiState.Controls].
 *
 * Also used by [MediaItemHelper] to prepare [MediaItem]s with the same information.
 */
class UiStateHelper(private val applicationContext: Context) {
    private val storageService = StorageService.getInstance(applicationContext)

    suspend fun articleAsAUiItem(article: Article, issueKey: AbstractIssueKey): AudioPlayerItem.UiItem {

        return AudioPlayerItem.UiItem(
            getAudioTitle(article),
            article.getAuthorNames(applicationContext),
            getAudioImage(article),
            null,
            OpenItemSpec.OpenIssueItemSpec(issueKey, article.key)
        )
    }

    fun podcastAsUiItem(section: Section): AudioPlayerItem.UiItem {
        val coverImageUri = getAudioImageForSection(section)
        val title = section.extendedTitle ?: section.title
        return AudioPlayerItem.UiItem(
            title,
            null,
            coverImageUri,
            null,
            null, // Podcasts shall not open the issue when being clicked in the player
        )
    }

    fun searchHitAsUiItem(searchHit: SearchHit, issueKey: AbstractIssueKey?): AudioPlayerItem.UiItem {
        return AudioPlayerItem.UiItem(
            title = searchHit.title,
            author = searchHit.getAuthorNames(),
            coverImageUri = null,
            coverImageGlidePath = null,
            issueKey?.let { OpenItemSpec.OpenIssueItemSpec(it, searchHit.audioPlayerPlayableKey) }
        )
    }

    fun podcastAsUiItem(page: Page): AudioPlayerItem.UiItem {
        val coverImageGlidePath = storageService.getAbsolutePath(page.pagePdf)
        val title = page.title ?: applicationContext.getString(R.string.audioplayer_podcast_generic)
        return AudioPlayerItem.UiItem(
            title,
            null,
            null,
            coverImageGlidePath,
            null, // Podcasts shall not open the issue when being clicked in the player
        )
    }


    private fun getAudioTitle(article: Article): String {
        return article.title ?: article.key
    }

    private fun getAudioImage(article: Article): Uri? {
        val articleImage = article.imageList.firstOrNull()
        val articleImageUriString = articleImage?.let { storageService.getFileUri(it) }
        return articleImageUriString?.let { Uri.parse(it) }
    }

    private fun getAudioImageForSection(section: Section): Uri? {
        val sectionImage = section.imageList.firstOrNull()
        val sectionImageUriString = sectionImage?.let { storageService.getFileUri(it) }
        return sectionImageUriString?.let { Uri.parse(it) }
    }

    fun getUiStateControls(
        playlist: Playlist, isAutoPlayNext: Boolean
    ): UiState.Controls {
        val skipNext = if (!playlist.isAtEnd()) {
            UiState.ControlValue.ENABLED
        } else {
            UiState.ControlValue.DISABLED
        }

        val skipPrevious = if (playlist.currentItemIdx > 0) {
            UiState.ControlValue.ENABLED
        } else {
            UiState.ControlValue.DISABLED
        }

        val autoPlayNext = if (isAutoPlayNext) {
            UiState.ControlValue.ENABLED
        } else {
            UiState.ControlValue.DISABLED
        }

        val seekBreaks = !playlist.getCurrentItem()?.audio?.breaks.isNullOrEmpty()

        return UiState.Controls(skipNext, skipPrevious, autoPlayNext, seekBreaks)

    }

    fun getDisclaimerUiItem(): AudioPlayerItem.UiItem = AudioPlayerItem.UiItem(
        applicationContext.getString(R.string.audioplayer_disclaimer_title),
        applicationContext.getString(R.string.audioplayer_disclaimer_author),
        null,
        null,
        null
    )

    fun getDisclaimerUiStateControls(): UiState.Controls = UiState.Controls(
        UiState.ControlValue.HIDDEN,
        UiState.ControlValue.HIDDEN,
        UiState.ControlValue.HIDDEN,
        seekBreaks = false
    )
}