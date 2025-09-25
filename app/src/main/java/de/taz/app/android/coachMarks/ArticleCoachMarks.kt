package de.taz.app.android.coachMarks

import android.view.View
import de.taz.app.android.R

class ArticleAudioCoachMark : BaseCoachMark(R.layout.coach_mark_article_audio) {
    companion object {
        fun create(menuItem: View) = ArticleAudioCoachMark().apply { this.menuItem = menuItem }
    }
}

class ArticleShareCoachMark : BaseCoachMark(R.layout.coach_mark_article_share) {
    companion object {
        fun create(menuItem: View) = ArticleShareCoachMark().apply { this.menuItem = menuItem }
    }
}

class ArticleSizeCoachMark : BaseCoachMark(R.layout.coach_mark_article_size) {
    companion object {
        fun create(menuItem: View) = ArticleSizeCoachMark().apply { this.menuItem = menuItem }
    }
}

class ArticleBookmarkCoachMark: BaseCoachMark(R.layout.coach_mark_article_bookmark) {
    companion object {
        fun create(menuItem: View) = ArticleBookmarkCoachMark().apply { this.menuItem = menuItem }
    }
}
class ArticleHomeCoachMark: BaseCoachMark(R.layout.coach_mark_article_home) {
    companion object {
        fun create(menuItem: View) = ArticleHomeCoachMark().apply { this.menuItem = menuItem }
    }
}

class ArticleImageCoachMark() : BaseCoachMark(R.layout.coach_mark_article_image)

class ArticleTapToScrollCoachMark() : BaseCoachMark(R.layout.coach_mark_article_tap_to_scroll)

class ArticleImagePagerCoachMark() : BaseCoachMark(R.layout.coach_mark_article_image_pager)

class ArticleSectionCoachMark: BaseCoachMark(R.layout.coach_mark_article_section) {
    companion object {
        fun create(menuItem: View, sectionTitle: String) = ArticleSectionCoachMark().apply {
            this.menuItem = menuItem
            this.textString = sectionTitle
            this.moveCloseButtonToWhereNextIs = true
        }
    }
}