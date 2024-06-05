package de.taz.app.android.appReview

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import de.taz.app.android.util.Log
import de.taz.app.android.sentry.SentryWrapper

class PlaystoreReviewFlow : ReviewFlow {
    private val log by Log

    override suspend fun startReviewFlow(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReview()
        manager.launchReview(activity, request)
    }

    override suspend fun tryStartReviewFlow(activity: Activity) {
        try {
            startReviewFlow(activity)
        } catch (e: ReviewException) {
            log.error("Could not launch the Playstore review flow (${e.errorCode}", e)
            SentryWrapper.captureException(e)
        }
    }
}