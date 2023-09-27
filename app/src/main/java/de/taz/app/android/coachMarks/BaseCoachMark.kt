package de.taz.app.android.coachMarks

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log


const val COACH_MARK_PRIO2 = 3L
const val COACH_MARK_PRIO3 = 6L
const val COACH_MARK_PRIO4 = 9L
const val MAX_TO_SHOW_IN_ONE_SESSION = 3

abstract class BaseCoachMark(private val context: Context, private val lifecycle: Lifecycle) {
    constructor(fragment: Fragment): this(fragment.requireContext(), fragment.lifecycle)

    private companion object {
        // To ensure that at most one CoachMark is shown, we store a simple flag.
        // Note that this flag is not thread safe, but as the CoachMarks are not really started in
        // parallel, and generally on the main thread we can get away with it.
        // In theory two coach marks could enter `maybeReallyShow()` at the same time.
        var isCoachMarkShown: Boolean = false
    }

    protected val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)
    protected val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    protected val authHelper = AuthHelper.getInstance(context.applicationContext)
    private val isPortrait =
        context.resources.displayMetrics.heightPixels > context.resources.displayMetrics.widthPixels
    private val isTabletMode = context.resources.getBoolean(R.bool.isTablet)
    private val showConditions = isPortrait || isTabletMode

    protected val log by Log

    suspend fun maybeShow() {
        val maxReached =
            coachMarkDataStore.coachMarksShownInSession.get() >= MAX_TO_SHOW_IN_ONE_SESSION
        val alwaysShow = coachMarkDataStore.alwaysShowCoachMarks.get()
        if (showConditions && authHelper.isLoggedIn() && (alwaysShow || !maxReached) && (alwaysShow || !isCoachMarkShown)) {
            maybeShowInternal()
        }
    }

    /**
     * Only called if the base conditions to show the coach mark have been checked in [maybeShow].
     * Coach mark implementation may add some additional checks required to show in this function.
     */
    abstract suspend fun maybeShowInternal()

    protected fun getLocationAndShowLayout(
        viewToLocate: View?,
        layoutResId: Int,
    ) {
        isCoachMarkShown = true
        val location = intArrayOf(0, 0)
        viewToLocate?.let { view ->
            view.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    view.getLocationOnScreen(location)
                    log.verbose("found location: ${location[0]}, ${location[1]}")
                    showCoachMark(location, layoutResId)
                }
            })
        } ?: showCoachMark(location, layoutResId)
    }

    private fun showCoachMark(location: IntArray, layoutResId: Int) {

        val coachMarkDialog = CoachMarkDialog(context, location, layoutResId)
        onCoachMarkCreated(coachMarkDialog)
        coachMarkDialog.show()
        coachMarkDialog.setOnDismissListener {
            // Release the coach mark lock
            isCoachMarkShown = false
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                coachMarkDialog.dismiss()
                lifecycle.removeObserver(this)
            }
        })
    }

    suspend fun incrementCoachMarksShownInSession() {
        coachMarkDataStore.coachMarksShownInSession.set(
            coachMarkDataStore.coachMarksShownInSession.get() + 1
        )
    }

    protected open fun onCoachMarkCreated(coachMarkDialog: CoachMarkDialog) {}
}
