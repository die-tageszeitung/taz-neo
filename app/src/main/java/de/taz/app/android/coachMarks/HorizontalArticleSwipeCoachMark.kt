package de.taz.app.android.coachMarks

import android.content.Context
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment

class HorizontalArticleSwipeCoachMark(articlePagerFragment: ArticlePagerFragment) : BaseCoachMark(articlePagerFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.horizontalArticleSwipeCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (!BuildConfig.IS_LMD) {
            // Do not show this Coach Mark if not LMd
            return
        }

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(null, R.layout.coach_mark_horizontal_swipe)
            return
        }

        val currentAppSession = generalDataStore.appSessionCount.get()
        val coachMarkArticleSizeCoachMarkShown = coachMarkDataStore.articleSizeCoachMarkShown.get()
        val coachMarkHorizontalSwipeShownOnSession =
            coachMarkDataStore.horizontalArticleSwipeCoachMarkShown.get()

        if (coachMarkHorizontalSwipeShownOnSession == 0L && currentAppSession >= coachMarkArticleSizeCoachMarkShown + COACH_MARK_PRIO2) {
            getLocationAndShowLayout(null, R.layout.coach_mark_horizontal_swipe)
            coachMarkDataStore.horizontalArticleSwipeCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}
