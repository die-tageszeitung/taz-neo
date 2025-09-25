package de.taz.app.android.coachMarks

import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import de.taz.app.android.R


class TazLogoCoachMark : BaseCoachMark(R.layout.coach_mark_taz_logo) {
    companion object {
        fun create(menuItem: ImageView) = TazLogoCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<ImageView>(R.id.drawer_logo)?.setImageBitmap(
            (this.menuItem as ImageView).drawable.toBitmap()
        )
        super.onCoachMarkCreated()
    }
}
// endregion

class LmdLogoCoachMark : BaseCoachMark(R.layout.coach_mark_lmd_logo) {
    companion object {
        fun create(menuItem: View) = LmdLogoCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }
}
// endregion