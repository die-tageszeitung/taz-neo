package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R

class ArchiveCoachMark(context: Context) : BaseCoachMark(context) {

    override suspend fun maybeShow() {

        if (authHelper.isLoggedIn()) {
            val currentAppSession = generalDataStore.appSessionCount.get()
            val coachMarkFabShownOnSession = coachMarkDataStore.fabCoachMarkShown.get()
            val coachMarkArchiveShownOnSession =
                coachMarkDataStore.archiveCoachMarkShown.get()

            if (
                coachMarkArchiveShownOnSession == 0L &&
                currentAppSession >= coachMarkFabShownOnSession + COACH_MARK_PRIO4
            ) {
                getLocationAndShowLayout(null, R.layout.coach_mark_archive)
                coachMarkDataStore.horizontalSwipeCoachMarkShown.set(
                    currentAppSession
                )
            }
        }
    }
}
