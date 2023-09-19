package de.taz.app.android.coachMarks

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
import de.taz.app.android.R


class DrawerLogoCoachMark(private val context: Context, private val drawerLogo: ImageView, private val logo: Drawable) :
    BaseCoachMark(context) {
    override suspend fun maybeShow() {

        if (authHelper.isLoggedIn()) {
            val currentAppSession = generalDataStore.appSessionCount.get()
            val coachMarkDrawerLogoShownOnSession =
                coachMarkDataStore.drawerLogoCoachMarkShown.get()

            if (coachMarkDrawerLogoShownOnSession == 0L) {
                getLocationAndShowLayout(drawerLogo, R.layout.coach_mark_taz_logo)
                coachMarkDataStore.drawerLogoCoachMarkShown.set(
                    currentAppSession
                )
            }
        }
    }

    override fun onCoachMarkCreated(coachMarkDialog: CoachMarkDialog) {
        // scale factor determined in resources
        val scaleFactor = context.resources.getFraction(
            R.fraction.nav_button_scale_factor,
            1,
            33
        )
        val logicalWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            logo.intrinsicWidth.toFloat(),
            context.resources.displayMetrics
        ) * scaleFactor

        val logicalHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            logo.intrinsicHeight.toFloat(),
            context.resources.displayMetrics
        ) * scaleFactor

        coachMarkDialog.findViewById<ImageView>(R.id.drawer_logo).apply {
            setImageDrawable(logo)
            layoutParams.width = logicalWidth.toInt()
            layoutParams.height = logicalHeight.toInt()
        }
    }
}