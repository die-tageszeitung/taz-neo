package de.taz.app.android.audioPlayer

import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section

sealed interface AudioPlayerItem {
//    fun openItem(activity: AppCompatActivity): Boolean = false
}


/**
 * Holds all the information required to play the audio file attached to an Article
 * and to render the player ui
 */
data class ArticleAudio(
    val issueStub: IssueStub,
    val article: Article,
) : AudioPlayerItem {

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
    // For now podcasts need to be bound to a Section
    val section: Section,
    val title: String,
    val audio: Audio,
) : AudioPlayerItem {

    override fun toString(): String {
        return "PodcastAudio(${issueStub.issueKey}, ${section.key}, $title)"
    }
}
