package de.taz.app.android.ui.home.page

import de.taz.app.android.api.models.Feed
import de.taz.app.android.data.DataService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.Log
import java.util.*

/**
 * As long as we cannot safely determine the next issue after the current we need this stupid construct
 */
// TODO: This needs to be replaced by remote pagination metadata!
class StupidIssuePaging(private val feed: Feed, private val dataService: DataService) {
    private val log by Log

    fun findNext(issueDate: Date): Date? {
        val index = DUMMY_FEED_LIST.indexOf(simpleDateFormat.format(issueDate))
        return if (index > 0 && index < DUMMY_FEED_LIST.size) {
            simpleDateFormat.parse(DUMMY_FEED_LIST[index - 1])
        } else {
            null
        }
    }

    fun findPrevious(issueDate: Date): Date? {
        val index = DUMMY_FEED_LIST.indexOf(simpleDateFormat.format(issueDate))
        return if (index > -1 && index < DUMMY_FEED_LIST.size) {
            simpleDateFormat.parse(DUMMY_FEED_LIST[index + 1])
        } else {
            null
        }
    }

    fun itemsBefore(issueDate: Date): Int {
        return DUMMY_FEED_LIST.indexOf(simpleDateFormat.format(issueDate))
    }

    fun itemsAfter(issueDate: Date): Int {
        return DUMMY_FEED_LIST.size - DUMMY_FEED_LIST.indexOf(simpleDateFormat.format(issueDate)) + 1
    }
}