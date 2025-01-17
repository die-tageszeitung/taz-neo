package de.taz.app.android.singletons

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.R
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.bookmarks.BookmarkListItem

/**
 * Singleton to simplify the creation of snackBars
 */
object SnackBarHelper {

    private fun showSnack(
        context: Context,
        view: View,
        anchor: View?,
        text: String,
        textAction: String,
        textActionColor: Int? = null,
        action: (context: Context) -> Unit?,
    ) {
        val snack = Snackbar.make(
            context,
            view,
            text,
            Snackbar.LENGTH_LONG,
        )
        snack.apply {
            anchorView = anchor
            textActionColor?.let { setActionTextColor(it) }
            setAction(textAction) {
                action(context)
            }
            // Additionally set the action on the whole snack bar:
            snack.view.setOnClickListener{
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
        context.getString(R.string.snack_article_bookmark_action),
        null,
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
        context.getString(R.string.snack_article_bookmark_action),
        null,
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
        context.getString(R.string.audioplayer_show_in_playlist),
        null,
    ) { showPlaylist(context) }

    fun showDebookmarkUndoSnack(
        context: Context,
        view: View,
        article: BookmarkListItem.Item,
        action: (article: BookmarkListItem.Item) -> Unit?,
    ) = showSnack(
        context, view,
        null,
        context.getString( R.string.fragment_bookmarks_deleted),
        context.getString(R.string.fragment_bookmarks_undo),
        ResourcesCompat.getColor(
            view.context.resources,
            R.color.fragment_bookmarks_delete_background,
            null
        ),
    ) {
        action(article)
    }

    private fun goToBookmarks(context: Context) {
        Intent(context, BookmarkListActivity::class.java).apply {
            context.startActivity(this)
        }
    }

    private fun showPlaylist(context: Context) {
        AudioPlayerService.getInstance(context.applicationContext).showPlaylist()
    }
}