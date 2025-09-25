package de.taz.app.android.coachMarks

import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.view.updateLayoutParams
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import de.taz.app.android.R

class PlayerMinimizeCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_minimize
) {
    companion object {
        fun create(menuItem: View) = PlayerMinimizeCoachMark().apply {
            this.menuItem = menuItem
            this.useShortArrow = true
        }
    }
}

class PlayerArticleCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_article
) {
    private var textString2: String? = null

    companion object {
        fun create(menuItem: View, article: String, author: String) =
            PlayerArticleCoachMark().apply {
                this.menuItem = menuItem
                this.textString = article
                this.textString2 = author
            }
    }

    override fun onCoachMarkCreated() {
        textString2?.let {
            view?.findViewById<TextView>(R.id.coach_mark_text_view_2)?.text = it
        }
        this.menuItem?.width?.let {
            view?.findViewById<View>(R.id.coach_mark_text_wrapper)
                ?.updateLayoutParams { width = it }
        }

        super.onCoachMarkCreated()
    }
}

class PlayerSpeedCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_speed
) {
    companion object {
        fun create(menuItem: View, speed: String) = PlayerSpeedCoachMark().apply {
            this.menuItem = menuItem
            this.textString = speed
        }
    }
}

class PlayerSliderCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_slider
) {
    companion object {
        fun create(menuItem: View) = PlayerSliderCoachMark().apply {
            this.menuItem = menuItem
            this.resizeIcon = true
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCoachMarkCreated() {
        val coachMarkIconWrapper =
            requireView().findViewById<RelativeLayout>(R.id.coach_mark_icon_wrapper)

        val slider = coachMarkIconWrapper.findViewById<DefaultTimeBar>(R.id.expanded_progress)
        slider?.apply {

            setPosition(200L)
            setDuration(600L)
            setEnabled(true)
        }
    }
}

class PlayerRewindCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_rewind
) {
    companion object {
        fun create(menuItem: View) = PlayerRewindCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}

class PlayerBackCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_back
) {
    companion object {
        fun create(menuItem: View) = PlayerBackCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}

class PlayerPlayCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_play
) {
    companion object {
        fun create(menuItem: View) = PlayerPlayCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}

class PlayerSkipCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_skip
) {
    companion object {
        fun create(menuItem: View) = PlayerSkipCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}

class PlayerForwardCoachMark : BaseCoachMark(
    R.layout.coach_mark_player_forward
) {
    companion object {
        fun create(menuItem: View) = PlayerForwardCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}