package de.taz.app.android.coachMarks

import android.content.Context
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment

class ArticleSizeCoachMark(articlePagerFragment: ArticlePagerFragment, private val menuItem: View) :
    BaseCoachMark(articlePagerFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.articleSizeCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(menuItem, R.layout.coach_mark_article_size)
            return
        }

        val currentAppSession = generalDataStore.appSessionCount.get()
        val thisCoachMarkShownOnSession = coachMarkDataStore.articleSizeCoachMarkShown.get()
        val articleAudioCoachMarkSownOnSession =
            coachMarkDataStore.articleAudioCoachMarkShown.get()

        if (thisCoachMarkShownOnSession == 0L && currentAppSession >= articleAudioCoachMarkSownOnSession + COACH_MARK_PRIO2) {
            getLocationAndShowLayout(menuItem, R.layout.coach_mark_article_size)
            coachMarkDataStore.articleSizeCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}