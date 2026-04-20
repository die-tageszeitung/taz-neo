package de.taz.app.android.coachMarks

import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import de.taz.app.android.R

class PdfDrawerPageCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_page
) {

    companion object {
        fun create(
            menuItem: View,
        ) = PdfDrawerPageCoachMark().apply {
            this.resizeIcon = true
            this.menuItem = menuItem
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<ImageView>(R.id.coach_mark_pdf_drawer_moment_image)?.setImageBitmap(
            (this.menuItem as ImageView).drawable.toBitmap()
        )
    }
}

class PdfDrawerPlayAllCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_play_all
) {
    companion object {
        fun create(menuItem: View) = PdfDrawerPlayAllCoachMark().apply {
            this.menuItem = menuItem
            this.verticalBias = 0.48f
        }
    }
}

class PdfDrawerSwitchViewToListCoachMark : BaseCoachMark(
    R.layout.coach_mark_pdf_drawer_switch_view_to_list
) {
    companion object {
        fun create(menuItem: View) = PdfDrawerSwitchViewToListCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}
