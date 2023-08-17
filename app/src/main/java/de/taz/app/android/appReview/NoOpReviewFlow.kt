package de.taz.app.android.appReview

import android.app.Activity

class NoOpReviewFlow : ReviewFlow {
    override suspend fun startReviewFlow(activity: Activity) = Unit
    override suspend fun tryStartReviewFlow(activity: Activity) = Unit
}