package de.taz.app.android.coachMarks

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log


const val COACH_MARK_PRIO1 = 0L
const val COACH_MARK_PRIO2 = 3L
const val COACH_MARK_PRIO3 = 6L
const val COACH_MARK_PRIO4 = 9L

abstract class BaseCoachMark(private val context: Context) {

    protected val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)
    protected val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    protected val authHelper = AuthHelper.getInstance(context.applicationContext)
    protected val log by Log

    abstract suspend fun maybeShow()
    protected fun getLocationAndShowLayout(
        viewToLocate: View?,
        layoutResId: Int,
    ) {
        val location = intArrayOf(0, 0)
        viewToLocate?.let { view ->
            view.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        view.getLocationOnScreen(location)
                        log.verbose("found location: ${location[0]}, ${location[1]}")
                        showCoachMark(location, layoutResId)
                    }
                }
            )
        } ?: showCoachMark(location, layoutResId)
    }

    private fun showCoachMark(location: IntArray, layoutResId: Int) {
        val coachMarkDialog = CoachMarkDialog(context, location, layoutResId)
        onCoachMarkCreated(coachMarkDialog)
        coachMarkDialog.show()
    }

    protected open fun onCoachMarkCreated(coachMarkDialog: CoachMarkDialog) {}
}
