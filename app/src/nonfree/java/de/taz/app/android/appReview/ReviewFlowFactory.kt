package de.taz.app.android.appReview

class ReviewFlowFactory: ReviewFlowFactoryInterface {
    override fun createInstance(): ReviewFlow {
        return PlaystoreReviewFlow()
    }
}