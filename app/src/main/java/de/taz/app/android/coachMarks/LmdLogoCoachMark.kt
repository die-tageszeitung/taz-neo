package de.taz.app.android.coachMarks

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ImageView
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity


class LmdLogoCoachMark(pdfPagerActivity: PdfPagerActivity, private val drawerLogo: ImageView, private val logo: Drawable) :
    BaseCoachMark(pdfPagerActivity) {

    private val context = pdfPagerActivity.applicationContext

    companion object {
        suspend fun setFunctionAlreadyDiscovered(context: Context) {
            val generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
            val coachMarkDataStore = CoachMarkDataStore.getInstance(context.applicationContext)

            val currentAppSession = generalDataStore.appSessionCount.get()
            coachMarkDataStore.lmdLogoCoachMarkShown.set(
                currentAppSession
            )
        }
    }

    override suspend fun maybeShowInternal() {

        if (!BuildConfig.IS_LMD) {
            // Do not show this Coach Mark if not LMd
            return
        }

        if (coachMarkDataStore.alwaysShowCoachMarks.get()) {
            getLocationAndShowLayout(drawerLogo, R.layout.coach_mark_lmd_logo)
            return
        }

        val coachMarkDrawerLogoShownOnSession =
            coachMarkDataStore.lmdLogoCoachMarkShown.get()

        if (coachMarkDrawerLogoShownOnSession == 0L) {
            val currentAppSession = generalDataStore.appSessionCount.get()
            getLocationAndShowLayout(drawerLogo, R.layout.coach_mark_lmd_logo)
            coachMarkDataStore.lmdLogoCoachMarkShown.set(
                currentAppSession
            )
            incrementCoachMarksShownInSession()
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