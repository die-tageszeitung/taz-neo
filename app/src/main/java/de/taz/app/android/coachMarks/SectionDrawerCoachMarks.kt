package de.taz.app.android.coachMarks

import android.view.View
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.ui.cover.CoverView
import de.taz.app.android.ui.home.page.CoverViewBinding

class SectionDrawerMomentCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_moment
) {
    private lateinit var coverViewData: CoverViewBinding

    companion object {
        fun create(
            menuItem: View,
            coverViewData: CoverViewBinding,
        ) = SectionDrawerMomentCoachMark().apply {
            this.resizeIcon = true
            this.menuItem = menuItem
            this.coverViewData = coverViewData
        }
    }

    override fun onCoachMarkCreated() {
        view?.findViewById<CoverView>(R.id.drawer_logo)?.let {
            coverViewData.prepareDataAndBind(it)
        }
    }
}

class SectionDrawerToggleAllCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_toggle_all
) {
    companion object {
        fun create(menuItem: View) = SectionDrawerToggleAllCoachMark().apply {
            this.resizeIcon = true
            this.menuItem = menuItem
            this.useShortArrow = true
        }
    }
}

class SectionDrawerSectionCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_section
) {
    companion object {
        fun create(menuItem: TextView) = SectionDrawerSectionCoachMark().apply {
            this.menuItem = menuItem
            this.textString = menuItem.text.toString()
            this.useShortArrow = true
        }
    }
}
class SectionDrawerPlayAllCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_play_all
) {
    companion object {
        fun create(menuItem: View) = SectionDrawerPlayAllCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}
class SectionDrawerToggleOneCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_toggle_one
)
class SectionDrawerEnqueueCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_enqueue
)
class SectionDrawerBookmarkCoachMark : BaseCoachMark(
    R.layout.coach_mark_section_drawer_bookmark
)