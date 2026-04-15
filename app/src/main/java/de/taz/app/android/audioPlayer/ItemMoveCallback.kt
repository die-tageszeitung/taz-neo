package de.taz.app.android.audioPlayer

import android.graphics.Canvas
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.util.Log

class ItemMoveCallback(private val audioPlayerService: AudioPlayerService) :
    ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    )
{
    private val log by Log

    private var draggedViewHolder: PlaylistViewHolder? = null
    private var swipedViewHolder: PlaylistViewHolder? = null
    private var initialFromPos = -1
    private var finalToPos = -1


    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Allow all viewHolders being moved
        return true
    }


    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                draggedViewHolder = viewHolder as? PlaylistViewHolder
                // Ensure indices are reset at the START of a new drag
                initialFromPos = -1
                finalToPos = -1
                // Ensures the element is dragged visually over the others
                viewHolder?.itemView?.elevation = 5f
            }

            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // If we have a valid move, notify the service
                if (initialFromPos != -1 && finalToPos != -1 && initialFromPos != finalToPos) {
                    audioPlayerService.moveItemInPlaylist(initialFromPos, finalToPos)
                }

                // Reset state immediately
                initialFromPos = -1
                finalToPos = -1

                // hide background indication deletion if not swiping
                swipedViewHolder?.itemView?.findViewById<View>(R.id.audioplayer_playlist_item_background)?.animate()?.alpha(0f)?.duration =
                    600L

                swipedViewHolder = null
                draggedViewHolder = null
            }

            ItemTouchHelper.ACTION_STATE_SWIPE -> {
                swipedViewHolder = viewHolder as? PlaylistViewHolder
                swipedViewHolder?.itemView?.findViewById<View>(R.id.audioplayer_playlist_item_background)?.alpha = 1f
            }

        }
    }

    override fun onMoved(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        fromPos: Int,
        target: RecyclerView.ViewHolder,
        toPos: Int,
        x: Int,
        y: Int
    ) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
        // Store the initial fromPos to update the AudioPlayerService when the drag is dropped
        if (initialFromPos == -1) {
            initialFromPos = fromPos
        }
        finalToPos = toPos

        val adapter = recyclerView.adapter as? PlaylistAdapter
        adapter?.intermediateMoveWhileDragged(fromPos, toPos)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (viewHolder is PlaylistViewHolder) {
            viewHolder.boundItem?.let { audioPlayerService.removeItemFromPlaylist(it) }
        } else {
            log.error("AudioPlayer ItemCallBack called with no PlaylistViewHolder")
        }
    }

    override fun onChildDrawOver(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder?,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (viewHolder is PlaylistViewHolder && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val foregroundView =
                viewHolder.itemView.findViewById<ConstraintLayout>(R.id.audioplayer_playlist_item_foreground)
            getDefaultUIUtil().onDrawOver(
                c,
                recyclerView,
                foregroundView,
                dX,
                dY,
                actionState,
                isCurrentlyActive
            )
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        // Reset the elevation and the UI utility translation
        viewHolder.itemView.elevation = 0f

        if (viewHolder is PlaylistViewHolder) {
            val foregroundView = viewHolder.itemView.findViewById<View>(R.id.audioplayer_playlist_item_foreground)
            getDefaultUIUtil().clearView(foregroundView)
        }
        super.clearView(recyclerView, viewHolder)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (viewHolder is PlaylistViewHolder && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val foregroundView =
                viewHolder.itemView.findViewById<ConstraintLayout>(R.id.audioplayer_playlist_item_foreground)

            getDefaultUIUtil().onDraw(
                c,
                recyclerView,
                foregroundView,
                dX,
                dY,
                actionState,
                isCurrentlyActive
            )
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}