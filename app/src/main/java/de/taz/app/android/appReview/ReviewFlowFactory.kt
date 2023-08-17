package de.taz.app.android.appReview

/**
 * This interface has to be implemented by all build variant dependent ReviewFlowFactorys.
 */
interface ReviewFlowFactoryInterface {
    fun createInstance(): ReviewFlow
}

fun createReviewFlowInstance(): ReviewFlow {
    val reviewFlowFactory: ReviewFlowFactoryInterface = ReviewFlowFactory()
    return reviewFlowFactory.createInstance()
}