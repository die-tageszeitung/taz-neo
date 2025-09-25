package de.taz.app.android.coachMarks

import android.view.View
import de.taz.app.android.R

class HomePresentationCoachMark() : BaseCoachMark(R.layout.coach_mark_home_presentation) {
    companion object {
        fun create(menuItem: View) = HomePresentationCoachMark().apply { this.menuItem = menuItem }
    }
}

class ArchiveDownloadCoachMark : BaseCoachMark(R.layout.coach_mark_archive_download)

class ArchiveContinueReadCoachMark : BaseCoachMark(R.layout.coach_mark_archive_continue_read)

class ArchiveDatePickerCoachMark() : BaseCoachMark(R.layout.coach_mark_archive_datepicker) {
    companion object {
        fun create(menuItem: View) = ArchiveDatePickerCoachMark().apply { this.menuItem = menuItem }
    }
}

class HomeHomeCoachMark() : BaseCoachMark(R.layout.coach_mark_home_home) {
    companion object {
        fun create(menuItem: View) = HomeHomeCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }
}

class HomeBookmarksCoachMark() : BaseCoachMark(R.layout.coach_mark_home_bookmarks) {
    companion object {
        fun create(menuItem: View) = HomeBookmarksCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }
}

class HomePlaylistCoachMark() : BaseCoachMark(R.layout.coach_mark_home_playlist) {
    companion object {
        fun create(menuItem: View) = HomePlaylistCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }
}

class HomeSearchCoachMark() : BaseCoachMark(R.layout.coach_mark_home_search) {
    companion object {
        fun create(menuItem: View) = HomeSearchCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }
}

class HomeSettingsCoachMark() : BaseCoachMark(R.layout.coach_mark_home_settings) {
    companion object {
        fun create(menuItem: View) = HomeSettingsCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }
}

class CoverflowDownloadCoachMark() : BaseCoachMark(R.layout.coach_mark_cover_flow_download) {
    companion object {
        fun create(menuItem: View) = CoverflowDownloadCoachMark().apply {
            this.menuItem = menuItem
            this.useShortArrow = true
        }
    }
}

class CoverflowContinueReadCoachMark() :
    BaseCoachMark(R.layout.coach_mark_cover_flow_continue_read) {
    companion object {
        fun create(menuItem: View) =
            CoverflowContinueReadCoachMark().apply { this.menuItem = menuItem }
    }
}

class CoverflowDatePickerCoachMark() : BaseCoachMark(R.layout.coach_mark_cover_flow_date_picker) {
    companion object {
        fun create(menuItem: View) =
            CoverflowDatePickerCoachMark().apply {
                this.menuItem = menuItem
            }
    }
}