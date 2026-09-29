package de.taz.app.android.coachMarks

import android.content.res.Configuration
import android.view.View
import android.widget.Button
import android.widget.ImageView
import de.taz.app.android.R
import de.taz.app.android.util.Log

class LatestEpisodeCoachMark : BaseCoachMark(
    R.layout.coach_mark_listen_latest_episode
) {
    companion object {
        fun create(menuItem: Button) = LatestEpisodeCoachMark().apply {
            this.menuItem = menuItem
            this.repositionLayout = true
        }
    }
}

class PodcastCarouselCoachMark : BaseCoachMark(
    R.layout.coach_mark_listen_podcast_carousel
) {
    companion object {
        fun create(menuItem: ImageView) = PodcastCarouselCoachMark().apply {
            this.menuItem = menuItem
        }
    }
}

class OlderEpisodesCoachMark : BaseCoachMark(
    R.layout.coach_mark_listen_older_episodes
)

class PlaylistTabCoachMark : BaseCoachMark(
    R.layout.coach_mark_listen_playlist
) {
    companion object {
        fun create(menuItem: View) = PlaylistTabCoachMark().apply {
            this.menuItem = menuItem
            iconHorizontalBias = 0.2f // Adjust slightly to the left for better alignment
            moveCloseButtonToWhereNextIs = true
        }
    }
}