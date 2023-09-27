package de.taz.app.android.coachMarks

import android.content.Context
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment

class ArticleAudioCoachMark(articlePagerFragment: ArticlePagerFragment, private val menuItem: View) :
    BaseCoachMark(articlePagerFragment) {

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.articleAudioCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(menuItem, R.layout.coach_mark_article_audio)
            return
        }

        val currentAppSession = generalDataStore.appSessionCount.get()
        val thisCoachMarkShownOnSession = coachMarkDataStore.articleAudioCoachMarkShown.get()

        if (thisCoachMarkShownOnSession == 0L) {
            getLocationAndShowLayout(menuItem, R.layout.coach_mark_article_audio)
            coachMarkDataStore.articleAudioCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
        }
    }
}