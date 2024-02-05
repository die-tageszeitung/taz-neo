package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment

class ArticleTapToScrollCoachMark(fragment: ArticlePagerFragment) :
    BaseCoachMark(fragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.articleTapToScrollCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(null, R.layout.coach_mark_article_tap_to_scroll)
            return
        }

        val currentAppSession = generalDataStore.appSessionCount.get()
        val thisCoachMarkShownOnSession = coachMarkDataStore.articleTapToScrollCoachMarkShown.get()

        if (thisCoachMarkShownOnSession == 0L) {
            getLocationAndShowLayout(null, R.layout.coach_mark_article_tap_to_scroll)
            coachMarkDataStore.articleTapToScrollCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}