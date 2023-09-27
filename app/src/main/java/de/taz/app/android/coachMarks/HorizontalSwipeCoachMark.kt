package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.webview.pager.SectionPagerFragment

class HorizontalSwipeCoachMark(sectionPagerFragment: SectionPagerFragment) : BaseCoachMark(sectionPagerFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.horizontalSwipeCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(null, R.layout.coach_mark_horizontal_swipe)
            return
        }

        val currentAppSession = generalDataStore.appSessionCount.get()
        val coachMarkTazLogoShownOnSession = coachMarkDataStore.drawerLogoCoachMarkShown.get()
        val coachMarkHorizontalSwipeShownOnSession =
            coachMarkDataStore.horizontalSwipeCoachMarkShown.get()

        if (coachMarkHorizontalSwipeShownOnSession == 0L && currentAppSession >= coachMarkTazLogoShownOnSession + COACH_MARK_PRIO2) {
            getLocationAndShowLayout(null, R.layout.coach_mark_horizontal_swipe)
            coachMarkDataStore.horizontalSwipeCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}
