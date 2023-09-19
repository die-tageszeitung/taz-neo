package de.taz.app.android.coachMarks

import android.content.Context
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.taz.app.android.R


class FabCoachMark(context: Context, private val fab: FloatingActionButton) :
    BaseCoachMark(context) {
    override suspend fun maybeShow() {

        if (authHelper.isLoggedIn()) {
            val pdfMode = generalDataStore.pdfMode.get()
            val currentAppSession = generalDataStore.appSessionCount.get()
            val coachMarkFabShownOnSession = coachMarkDataStore.fabCoachMarkShown.get()

            if (!pdfMode && coachMarkFabShownOnSession == 0L) {
                getLocationAndShowLayout(fab, R.layout.coach_mark_fab)
                coachMarkDataStore.fabCoachMarkShown.set(
                    currentAppSession
                )
            }
        }
    }
}