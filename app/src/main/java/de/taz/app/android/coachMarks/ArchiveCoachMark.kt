package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.home.HomeFragment

class ArchiveCoachMark(homeFragment: HomeFragment) : BaseCoachMark(homeFragment.requireContext(), homeFragment.lifecycle) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.archiveCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(null, R.layout.coach_mark_archive)
            return
        }

        val currentAppSession = generalDataStore.appSessionCount.get()
        val coachMarkFabShownOnSession = coachMarkDataStore.fabCoachMarkShown.get()
        val coachMarkArchiveShownOnSession = coachMarkDataStore.archiveCoachMarkShown.get()

        if (coachMarkArchiveShownOnSession == 0L && currentAppSession >= coachMarkFabShownOnSession + COACH_MARK_PRIO4) {
            getLocationAndShowLayout(null, R.layout.coach_mark_archive)
            coachMarkDataStore.archiveCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}
