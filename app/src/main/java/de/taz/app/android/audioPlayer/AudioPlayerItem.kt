package de.taz.app.android.audioPlayer

import android.net.Uri
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.AbstractIssuePublication

sealed interface AudioPlayerItem {
    /**
     * [Audio] for the currently active item.
     */
    val audio: Audio
}


/**
 * Holds all the information required to play the audio file attached to an Article
 * and to render the player ui
 */
data class ArticleAudio(
    val issueStub: IssueStub,
    val article: Article,
) : AudioPlayerItem {

    override val audio: Audio = requireNotNull(article.audio)

    override fun toString(): String {
        return "ArticleAudio(${article.key})"
    }
}

data class IssueAudio(
    val issueStub: IssueStub,
    /** Articles with a non-null Audio */
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

            if (first.issueStub != second.issueStub) return false
            if (first.startIndex != second.startIndex) return false
            if (first.currentIndex != second.currentIndex) return false

            return true
        }
    }

    val currentArticle: Article = articles[currentIndex]

    override val audio: Audio = requireNotNull(currentArticle.audio)

    override fun toString(): String {
        return "IssueAudio(${issueStub.issueKey}, #$currentIndex)"
    }

    override fun equals(other: Any?): Boolean {
        // Only check shallow equality
        return (other is IssueAudio && equalsShallow(this, other))
    }
}

data class PodcastAudio(
    val issueStub: IssueStub,
    // Warning: It is not optimal to store the full Section instance as a property of PodcastAudio as
    // a Section contains multiple deeply nested data structures (lists of articles with lists of images ...).
    // And all of these have to be compared all when using the default equals() method on PodcastAudio.
    // But as we know in this specific case, that the Section of type "podcast" won't contain any Articles
    // we can keep the full Section instance for now.
    val section: Section?,
    val page: Page?,
    override val audio: Audio,
) : AudioPlayerItem {

    override fun toString(): String {
        return "PodcastAudio(${issueStub.issueKey}, section=${section?.key}, page=${page?.pagePdf?.name})"
    }
}
