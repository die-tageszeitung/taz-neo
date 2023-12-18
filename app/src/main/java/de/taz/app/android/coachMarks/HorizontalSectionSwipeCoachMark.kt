package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.webview.pager.SectionPagerFragment

class HorizontalSectionSwipeCoachMark(sectionPagerFragment: SectionPagerFragment) : BaseCoachMark(sectionPagerFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.horizontalSectionSwipeCoachMarkShown.set(
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
        val coachMarkTazLogoShownOnSession = coachMarkDataStore.tazLogoCoachMarkShown.get()
        val coachMarkHorizontalSwipeShownOnSession =
            coachMarkDataStore.horizontalSectionSwipeCoachMarkShown.get()

        if (coachMarkHorizontalSwipeShownOnSession == 0L && currentAppSession >= coachMarkTazLogoShownOnSession + COACH_MARK_PRIO2) {
            getLocationAndShowLayout(null, R.layout.coach_mark_horizontal_swipe)
            coachMarkDataStore.horizontalSectionSwipeCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}
