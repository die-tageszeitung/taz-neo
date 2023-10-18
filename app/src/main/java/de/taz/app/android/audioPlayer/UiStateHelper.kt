package de.taz.app.android.audioPlayer

import android.content.Context
import android.net.Uri
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.singletons.StorageService

/**
 * Helper classes to map [AudioPlayerItem]s to [UiState.Item]s and get [UiState.Controls].
 *
 * Also used by [MediaItemHelper] to prepare [MediaItem]s with the same information.
 */
class UiStateHelper(private val applicationContext: Context) {
    private val storageService = StorageService.getInstance(applicationContext)

    fun asUiItem(audioPlayerItem: AudioPlayerItem): UiState.Item = audioPlayerItem.run {
        when (this) {
            is ArticleAudio -> articleAsAUiItem(article)
            is IssueAudio -> articleAsAUiItem(currentArticle)
            is PodcastAudio -> podcastAsUiItem(this)
        }
    }

    private fun articleAsAUiItem(article: Article): UiState.Item {
        val authorText = getAudioAuthor(article)
            ?.let {
                applicationContext.getString(R.string.audioplayer_author_template, it)
            }

        return UiState.Item(
            getAudioTitle(article),
            authorText,
            getAudioImage(article)
        )
    }

    fun getAudioTitle(article: Article): String {
        return article.title ?: article.key
    }

    fun getAudioAuthor(article: Article): String? {
        return article.authorList
            .mapNotNull { it.name.takeUnless { it.isNullOrBlank() } }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString (", " )
    }

    fun getAudioImage(article: Article): Uri? {
        val articleImage = article.imageList.firstOrNull()
        val articleImageUriString = articleImage?.let { storageService.getFileUri(it) }
        return articleImageUriString?.let { Uri.parse(it) }
    }

    private fun getAudioImageForPodcast(section: Section): Uri? {
        val sectionImage = section.imageList.firstOrNull()
        val sectionImageUriString = sectionImage?.let { storageService.getFileUri(it) }
        return sectionImageUriString?.let { Uri.parse(it) }
    }

    private fun podcastAsUiItem(podcastAudio: PodcastAudio): UiState.Item {
        return UiState.Item(
            podcastAudio.title,
            null,
            getAudioImageForPodcast(podcastAudio.section),
        )
    }

    fun getUiStateControls(
        audioPlayerItem: AudioPlayerItem, isAutoPlayNext: Boolean
    ): UiState.Controls = audioPlayerItem.run {
        when (this) {
            is ArticleAudio -> UiState.Controls(
                UiState.ControlValue.HIDDEN,
                UiState.ControlValue.HIDDEN,
                UiState.ControlValue.HIDDEN
            )

            is IssueAudio -> {
                val skipNext = if (isAutoPlayNext || currentIndex < articles.lastIndex) {
                    UiState.ControlValue.ENABLED
                } else {
                    UiState.ControlValue.DISABLED
                }
                val skipPrevious = if (isAutoPlayNext || currentIndex > 0) {
                    UiState.ControlValue.ENABLED
                } else {
                    UiState.ControlValue.DISABLED
                }
                UiState.Controls(skipNext, skipPrevious, UiState.ControlValue.ENABLED)
            }

            is PodcastAudio -> UiState.Controls(
                UiState.ControlValue.HIDDEN,
                UiState.ControlValue.HIDDEN,
                UiState.ControlValue.HIDDEN
            )
        }
    }
}