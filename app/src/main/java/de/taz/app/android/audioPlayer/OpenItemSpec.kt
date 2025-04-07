package de.taz.app.android.audioPlayer

import de.taz.app.android.persistence.repository.AbstractIssueKey

/**
 * This specification is used to open an item in the audio player when being clicked on.
 * It must contain all the information required to navigate to the context of the item.
 * It must be comparable by its values (like data classes are by default).
 * and thus must not contain [Intent]s or references to lambda callback functions.
 */
sealed interface OpenItemSpec {
    data class OpenIssueItemSpec(val issueKey: AbstractIssueKey, val displayableKey: String) :
        OpenItemSpec
}