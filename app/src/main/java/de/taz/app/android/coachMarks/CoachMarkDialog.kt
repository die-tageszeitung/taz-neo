package de.taz.app.android.coachMarks

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.view.WindowCompat
import de.taz.app.android.R


class CoachMarkDialog(context: Context, location: IntArray, layoutResId: Int) :
    Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    init {
        this.setContentView(layoutResId)

        setPosition(location)
        this.setCancelable(false)

        findViewById<ImageView>(R.id.button_close)?.setOnClickListener {
            this.dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Move content beneath status bar:
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
        }
    }

    /**
     * The [CoachMarkDialog] has been given a [location]. The given layouts
     * may contain an arrow [R.id.coach_mark_arrow] and
     * an icon [R.id.coach_mark_icon_wrapper] which then can be positioned.
     */
    private fun setPosition(location: IntArray) {

        val coachMarkIconWrapper = findViewById<RelativeLayout>(R.id.coach_mark_icon_wrapper)
        val arrow = findViewById<View>(R.id.coach_mark_arrow)

        coachMarkIconWrapper?.apply {
            x = location[0].toFloat()
            y = location[1].toFloat()
        }

        arrow?.apply {
            x = location[0].toFloat() + this.paddingStart -this.paddingEnd
            y = location[1].toFloat() - this.paddingBottom + this.paddingTop
        }
    }
}