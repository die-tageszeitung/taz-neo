package de.taz.app.android.audioPlayer

import androidx.media3.common.MediaItem
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.AbstractIssueKey

sealed interface AudioPlayerItem {
    val issueKey: AbstractIssueKey
    val currentArticle: Article
    fun contains(mediaItem: MediaItem): Boolean
    fun copyFor(mediaItem: MediaItem): AudioPlayerItem
}

/**
 * Holds all the information required to play the audio file attached to an Article
 * and to render the player ui
 */
data class ArticleAudio(
    override val issueKey: AbstractIssueKey,
    val baseUrl: String,
    val article: Article,
) : AudioPlayerItem {

    override val currentArticle: Article = article

    override fun contains(mediaItem: MediaItem): Boolean =
        article.key == mediaItem.mediaId

    override fun copyFor(mediaItem: MediaItem): AudioPlayerItem {
        check(contains(mediaItem))
        return copy()
    }

    override fun toString(): String {
        return "ArticleAudio(${article.key})"
    }
}


data class IssueAudio(
    override val issueKey: AbstractIssueKey,
    val baseUrl: String,
    // only articles with audio btw.
    val articles: List<Article>,
    val startIndex: Int,
    val currentIndex: Int,
) : AudioPlayerItem {


    companion object {
        /**
         * Compare [IssueAudio] instances ignoring the list of [articles].
         */
        fun equalsShallow(first: IssueAudio?, second: IssueAudio?): Boolean {
            if (first === second) return true
            if (first == null || second == null) return false
            if (first.javaClass != second.javaClass) return false

            if (first.issueKey != second.issueKey) return false
            if (first.baseUrl != second.baseUrl) return false
            if (first.startIndex != second.startIndex) return false
            if (first.currentIndex != second.currentIndex) return false

            return true
        }
    }

    override val currentArticle: Article = articles[currentIndex]

    override fun contains(mediaItem: MediaItem): Boolean =
        indexOf(mediaItem) >= 0

    fun indexOf(mediaItem: MediaItem): Int =
        articles.indexOfFirst { it.key == mediaItem.mediaId }

    override fun copyFor(mediaItem: MediaItem): AudioPlayerItem {
        val index = indexOf(mediaItem)
        check(index >= 0)
        return copy(currentIndex = index)
    }

    override fun toString(): String {
        return "IssueAudio($issueKey,#$currentIndex)"
    }

    override fun equals(other: Any?): Boolean {
        // Only check shallow equality
        return (other is IssueAudio && equalsShallow(this, other))
    }
}