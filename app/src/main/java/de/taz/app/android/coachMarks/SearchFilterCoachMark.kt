package de.taz.app.android.coachMarks

import android.content.Context
import android.widget.ImageView
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.search.SearchActivity


class SearchFilterCoachMark(searchActivity: SearchActivity, private val view: ImageView) :
    BaseCoachMark(searchActivity, searchActivity.lifecycle) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.searchFilterCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(view, R.layout.coach_mark_search_filter)
            return
        }

        coachMarkDataStore.searchActivityShown.set(
            coachMarkDataStore.searchActivityShown.get() + 1L
        )

        val currentAppSession = generalDataStore.appSessionCount.get()
        val thisCoachMarkShownOnSession = coachMarkDataStore.searchFilterCoachMarkShown.get()
        val searchActivityShownAmount = coachMarkDataStore.searchActivityShown.get()

        if (thisCoachMarkShownOnSession == 0L && searchActivityShownAmount > COACH_MARK_PRIO4) {
            getLocationAndShowLayout(view, R.layout.coach_mark_search_filter)
            coachMarkDataStore.searchFilterCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}