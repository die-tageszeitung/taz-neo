package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R

class HorizontalSwipeCoachMark(context: Context) : BaseCoachMark(context) {

    override suspend fun maybeShow() {

        if (authHelper.isLoggedIn()) {
            val currentAppSession = generalDataStore.appSessionCount.get()
            val coachMarkTazLogoShownOnSession = coachMarkDataStore.drawerLogoCoachMarkShown.get()
            val coachMarkHorizontalSwipeShownOnSession =
                coachMarkDataStore.horizontalSwipeCoachMarkShown.get()

            if (
                coachMarkHorizontalSwipeShownOnSession == 0L &&
                currentAppSession >= coachMarkTazLogoShownOnSession + COACH_MARK_PRIO2
            ) {
                getLocationAndShowLayout(null, R.layout.coach_mark_horizontal_swipe)
                coachMarkDataStore.horizontalSwipeCoachMarkShown.set(
                    currentAppSession
                )
            }
        }
    }
}
