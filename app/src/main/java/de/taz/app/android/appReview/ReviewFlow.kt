package de.taz.app.android.appReview

import android.app.Activity

interface ReviewFlow {
    /**
     * Start the review flow for this build variant.
     */
    suspend fun startReviewFlow(activity: Activity)

    /**
     * Start the review flow for this build variant,
     * but ignore all errors.
     */
    suspend fun tryStartReviewFlow(activity: Activity)

    companion object {
        fun createInstance() = createReviewFlowInstance()
    }
}