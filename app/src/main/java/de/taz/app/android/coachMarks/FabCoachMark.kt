package de.taz.app.android.coachMarks

import android.content.Context
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.home.HomeFragment


class FabCoachMark(homeFragment: HomeFragment, private val fab: FloatingActionButton) :
    BaseCoachMark(homeFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.fabCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (BuildConfig.IS_LMD) {
            // Do not show this Coach Mark for LMd
            return
        }

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(fab, R.layout.coach_mark_fab)
            return
        }

        val pdfMode = generalDataStore.pdfMode.get()
        val currentAppSession = generalDataStore.appSessionCount.get()
        val coachMarkFabShownOnSession = coachMarkDataStore.fabCoachMarkShown.get()

        if (!pdfMode && coachMarkFabShownOnSession == 0L) {
            getLocationAndShowLayout(fab, R.layout.coach_mark_fab)
            coachMarkDataStore.fabCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}