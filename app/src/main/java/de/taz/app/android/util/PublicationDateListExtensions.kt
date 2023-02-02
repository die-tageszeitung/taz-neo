package de.taz.app.android.util

import de.taz.app.android.api.models.PublicationDate
import java.util.Date

/**
 * Returns position of [search] date in List<PublicationDate>
 * or (negated insertion position - 1) for [search] in List<PublicationDate>
 * so that the list remains sorted upon inserting [search]
 */
private fun List<PublicationDate>.binarySearchDateInDescendingList (search: Date) : Int {
    return this.binarySearch { publicationDate ->
        val cmp = publicationDate.date.compareTo(search)

        // As the publication dates are sorted in a descending order
        // and per default binary search operates on ascending lists
        // we have to invert the comparison
        cmp * -1
    }
}

/**
 * Returns index of [search] in List<PublicationDate>
 * or -1 if [search] not found
 */
fun List<PublicationDate>.getIndexOfDate(search: Date): Int {
    // we extra check whether [search] is at the beginning of the list,
    // since in that case binarySearch is inefficient.
    // And we have the case often (e.g. skipping to the newest issue in CoverFlow)
    if (this.getOrNull(0)?.date == search) {
        return 0
    }
    val index = this.binarySearchDateInDescendingList(search)
    if (index < 0) {
        return -1
    }
    return index
}

/**
 * Returns closest newer date to [search] or null for edge cases.
 * Requires List<PublicationDate> to be sorted in a descending fashion,
 * which is the form we receive it in from the API.
 */
fun List<PublicationDate>.getSuccessor(search: Date): Date? {
    val invertedPosition = this.binarySearchDateInDescendingList(search)
    val insertionPositionForDate = -(invertedPosition + 1)
    val successorIndex = insertionPositionForDate - 1
    return this.getOrNull(successorIndex)?.date
}
