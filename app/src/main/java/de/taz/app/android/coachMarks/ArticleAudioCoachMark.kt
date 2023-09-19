package de.taz.app.android.coachMarks

import android.content.Context
import android.view.View
import de.taz.app.android.R

class ArticleAudioCoachMark(context: Context, private val menuItem: View) :
    BaseCoachMark(context) {
    override suspend fun maybeShow() {

        if (authHelper.isLoggedIn()) {
            val currentAppSession = generalDataStore.appSessionCount.get()
            val thisCoachMarkShownOnSession = coachMarkDataStore.articleAudioCoachMarkShown.get()

            if (thisCoachMarkShownOnSession == 0L) {
                getLocationAndShowLayout(menuItem, R.layout.coach_mark_article_audio)
                coachMarkDataStore.articleAudioCoachMarkShown.set(
                    currentAppSession
                )
            }
        }
    }
}