package de.taz.app.android.singletons

import android.content.Context
import android.content.Intent
import android.view.View
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.R
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.ui.bookmarks.BookmarkListActivity

/**
 * Singleton to simplify the creation of snackBars
 */
object SnackBarHelper {

    fun showSnack(
        context: Context,
        view: View,
        anchor: View?,
        text: String,
        textAction: String,
        action: (context: Context) -> Unit?,
    ) {
        val snack = Snackbar.make(
            context,
            view,
            text,
            Snackbar.LENGTH_LONG
        )
        snack.apply {
            anchorView = anchor
            setAction(textAction) {
                action(context)
            }
            show()
        }
    }

    fun showBookmarkSnack(
        context: Context,
        view: View,
        anchor: View? = null,
    ) = showSnack(
        context,
        view,
        anchor,
        context.getString(R.string.snack_article_bookmarked),
        context.getString(R.string.snack_article_bookmark_action)
    ) { goToBookmarks(context) }

    fun showDebookmarkSnack(
        context: Context,
        view: View,
        anchor: View? = null,
    ) = showSnack(
        context,
        view,
        anchor,
        context.getString(R.string.snack_article_debookmarked),
        context.getString(R.string.snack_article_bookmark_action)
    ) { goToBookmarks(context) }

    fun showPlayListSnack(
        context: Context,
        view: View,
        anchor: View?,
    ) = showSnack(
        context,
        view,
        anchor,
        context.getString(R.string.audioplayer_snackbar_added_to_playlist),
        context.getString(R.string.audioplayer_show_in_playlist)
    ) { showPlaylist(context) }

    private fun goToBookmarks(context: Context) {
        Intent(context, BookmarkListActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    private fun showPlaylist(context: Context) {
        AudioPlayerService.getInstance(context.applicationContext).showPlaylist()
    }
}