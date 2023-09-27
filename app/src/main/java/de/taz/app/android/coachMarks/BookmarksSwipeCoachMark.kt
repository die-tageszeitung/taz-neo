package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.bookmarks.BookmarkListFragment

class BookmarksSwipeCoachMark(bookmarkListFragment: BookmarkListFragment) : BaseCoachMark(bookmarkListFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.bookmarksSwipeCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(null, R.layout.coach_mark_bookmarks_swipe)
            return
        }

        coachMarkDataStore.bookmarksShown.set(
            coachMarkDataStore.bookmarksShown.get() + 1L
        )
        val currentAppSession = generalDataStore.appSessionCount.get()
        val thisCoachMarkShownOnSession = coachMarkDataStore.bookmarksSwipeCoachMarkShown.get()
        val bookmarksShownAmount = coachMarkDataStore.bookmarksShown.get()

        if (thisCoachMarkShownOnSession == 0L && bookmarksShownAmount > COACH_MARK_PRIO3) {
            getLocationAndShowLayout(null, R.layout.coach_mark_bookmarks_swipe)
            coachMarkDataStore.bookmarksSwipeCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}
