package de.taz.app.android.coachMarks

import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import de.taz.app.android.R
import de.taz.app.android.ui.logo.LogoView


class TazLogoCoachMark : BaseCoachMark(R.layout.coach_mark_taz_logo) {
    companion object {
        fun create(menuItem: LogoView) = TazLogoCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<ImageView>(R.id.feed_logo) ?:
        view?.findViewById<ImageView>(R.id.burger_logo)?.setImageBitmap(
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

class BurgerMenuCoachMark : BaseCoachMark(R.layout.coach_mark_burger_menu) {
    companion object {
        fun create(menuItem: ImageView) = BurgerMenuCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<ImageView>(R.id.burger_logo)?.setImageBitmap(
            (this.menuItem as ImageView).drawable.toBitmap()
        )
        super.onCoachMarkCreated()
    }
}
