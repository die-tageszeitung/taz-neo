package de.taz.app.android.audioPlayer

import android.net.Uri
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.AbstractIssueKey

/**
 * Holds all the information required to play the audio file attached to an Article
 * and to render the player ui
 */
data class ArticleAudio(
    val audioFileUrl: Uri,
    val article: Article,
    val issueKey: AbstractIssueKey,
)