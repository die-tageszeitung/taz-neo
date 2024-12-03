package de.taz.app.android.audioPlayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import de.taz.app.android.databinding.AudioplayerPlaylistItemBinding
import java.util.LinkedList

// FIXME: maybe dont pass the whole service but only functions required from here
class PlaylistViewHolder(
    private val audioPlayerService: AudioPlayerService,
    private val binding: AudioplayerPlaylistItemBinding
) : ViewHolder(binding.root) {

    var boundItem: AudioPlayerItem? = null

    init {
        binding.root.setOnClickListener {
            boundItem?.let {
                audioPlayerService.skipToItem(it)
            }
        }
    }

    fun bind(item: AudioPlayerItem, isCurrent: Boolean) {
        boundItem = item

        if (isCurrent) {
            if (audioPlayerService.isPlaying()) {
                binding.currentPlayingIndicator.isVisible = true
                binding.currentPausedIndicator.isVisible = false
                binding.dragIcon.isVisible = false
            } else  {
                binding.currentPausedIndicator.isVisible = true
                binding.currentPlayingIndicator.isVisible = false
                binding.dragIcon.isVisible = false
            }
        } else {
            binding.dragIcon.isVisible = true
            binding.currentPausedIndicator.isVisible = false
            binding.currentPlayingIndicator.isVisible = false
        }

        binding.title.text = item.uiItem.title
        binding.author.text = item.uiItem.author ?: ""
        binding.authorDurationWhitespace.isGone = item.uiItem.author.isNullOrEmpty()
        binding.duration.text = "${item.audio.playtime} min"
    }
}

class PlaylistAdapter(private val audioPlayerService: AudioPlayerService) :
    ListAdapter<AudioPlayerItem, PlaylistViewHolder>(AudioPlayerItemDiffCallBack()) {

    private var currentItem: Int? = null

    class AudioPlayerItemDiffCallBack : DiffUtil.ItemCallback<AudioPlayerItem>() {
        override fun areItemsTheSame(oldItem: AudioPlayerItem, newItem: AudioPlayerItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: AudioPlayerItem, newItem: AudioPlayerItem
        ): Boolean {
            // As we already override AudioPlayerItem.equals with optimized version, we have to do
            // the contents check here manually

            return oldItem.id == newItem.id && oldItem.uiItem == newItem.uiItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val binding = AudioplayerPlaylistItemBinding.inflate(LayoutInflater.from(parent.context))
        return PlaylistViewHolder(audioPlayerService, binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val item = getItem(position)
        val isCurrentlyPlaying = position == currentItem
        holder.bind(item, isCurrentlyPlaying)
    }

    /**
     * While dragging we will only update the state of the PlaylistAdapter, not the state of the
     * AudioPlayerService which is the actual source of truth.
     * Once the end of the drag is detected, the AudioPlayerService will be informed of the move.
     */
    fun intermediateMoveWhileDragged(fromPos: Int, toPos: Int) {
        val intermediateItems = LinkedList(currentList)
        val item = intermediateItems.removeAt(fromPos)
        intermediateItems.add(toPos, item)
        submitList(intermediateItems)
    }

    fun submitPlaylist(playlist: Playlist) {
        submitList(playlist.items) {
            val oldPosition = currentItem
            currentItem = playlist.currentItemIdx
            oldPosition?.let { notifyItemChanged(it) }
            currentItem?.let { notifyItemChanged(it) }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val itemMoveCallback = ItemMoveCallback(audioPlayerService)
        val itemTouchHelper = ItemTouchHelper(itemMoveCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}