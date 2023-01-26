package de.taz.app.android.ui.webview.pager

import android.view.MenuItem
import androidx.annotation.DrawableRes
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R

class ArticleBottomActionBarNavigationHelper(
    private val bottomNavigationView: BottomNavigationView,
    private val onClickHandler: (MenuItem) -> Unit
) {
    init {
        bottomNavigationView.apply {
            menu.clear()
            inflateMenu(R.menu.navigation_bottom_article)

            setOnItemSelectedListener {
                onClickHandler(it)
                false
            }
        }
    }

    private fun setIcon(itemId: Int, @DrawableRes iconRes: Int) {
        bottomNavigationView.apply {
            // prevent call while layouting
            post {
                menu.findItem(itemId)?.setIcon(iconRes)
            }
        }
    }

    private fun setVisibility(itemId: Int, isVisible: Boolean) {
        bottomNavigationView.menu.findItem(itemId)?.isVisible = isVisible
    }

    /**
     * Determine the share icon visibility: Hence the article is public or the [onlineLink] is not null
     * @param onlineLink String holding the link to be shared
     * @param articleKey String holding the key of the article (or for search hit the filename)
     * @return true if the share icon should be shown
     */
    private fun determineShareIconVisibility(onlineLink: String?, articleKey: String?): Boolean {
        return articleKey != null && articleKey.endsWith("public.html") || onlineLink != null
    }

    /**
     * Set the share icon visibility: Hence the article is public or the [onlineLink] is not null
     * @param onlineLink String holding the link to be shared
     * @param articleKey String holding the key of the article (or for search hit the filename)
     */
    fun setShareIconVisibility(onlineLink: String?, articleKey: String?) {
        setVisibility(
            R.id.bottom_navigation_action_share,
            determineShareIconVisibility(onlineLink, articleKey)
        )
    }

    fun setArticleAudioVisibility(isVisible: Boolean) {
        setVisibility(
            R.id.bottom_navigation_action_audio,
            isVisible
        )
    }

    fun setArticleAudioMenuIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            R.drawable.ic_audio_filled
        } else {
            R.drawable.ic_audio
        }
        setIcon(R.id.bottom_navigation_action_audio, icon)
    }

    fun setBookmarkIcon(isBookmarked: Boolean) {
        val icon = if (isBookmarked) {
            R.drawable.ic_bookmark_filled
        } else {
            R.drawable.ic_bookmark
        }
        setIcon(R.id.bottom_navigation_action_bookmark, icon)
    }
}