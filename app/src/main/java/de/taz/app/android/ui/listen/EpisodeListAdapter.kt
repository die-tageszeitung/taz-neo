package de.taz.app.android.ui.listen

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import de.taz.app.android.R
import de.taz.app.android.api.models.PodcastEpisode
import de.taz.app.android.audioPlayer.CIRCULAR_PROGRESS_TICKS
import de.taz.app.android.databinding.ItemEpisodeBinding
import de.taz.app.android.databinding.ItemEpisodeLatestBinding
import de.taz.app.android.databinding.ItemPodcastLoadingMoreBinding
import java.text.SimpleDateFormat
import java.util.Locale

// when an episode is played 98% we can mark it as played
const val MARK_AS_PLAYED_THRESHOLD = 0.98f

sealed class EpisodeListItem {
    data class Episode(val podcastEpisode: PodcastEpisode) : EpisodeListItem()
    object LoadingMore : EpisodeListItem()
}

class EpisodeListAdapter(
    private val onPlayClicked: (PodcastEpisode) -> Unit
) : ListAdapter<EpisodeListItem, RecyclerView.ViewHolder>(EpisodeDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private var currentlyPlayingEpisodeId: Int? = null
    private var isPlaying: Boolean = false
    private var currentProgressFraction: Float = 0f

    companion object {
        private const val VIEW_TYPE_LATEST = 0
        private const val VIEW_TYPE_EPISODE = 1
        private const val VIEW_TYPE_LOADING_MORE = 2
        private const val PAYLOAD_PLAYING_STATE = "PAYLOAD_PLAYING_STATE"
        private const val MARK_AS_PLAYED_THRESHOLD = 0.99f
    }

    fun setPlayingState(episodeId: Int?, isPlaying: Boolean) {
        val oldId = currentlyPlayingEpisodeId
        val oldPlaying = this.isPlaying

        if (oldId == episodeId && oldPlaying == isPlaying) return

        this.currentlyPlayingEpisodeId = episodeId
        this.isPlaying = isPlaying
        if (oldId != episodeId) {
            currentProgressFraction = 0f
        }

        // Notify changes for the affected items
        currentList.forEachIndexed { index, item ->
            if (item is EpisodeListItem.Episode) {
                val episode = item.podcastEpisode
                if (episode.id == oldId || episode.id == episodeId) {
                    notifyItemChanged(index, PAYLOAD_PLAYING_STATE)
                }
            }
        }
    }

    fun setProgress(episodeId: Int?, progress: Float) {
        if (episodeId == null) return
        if (episodeId == currentlyPlayingEpisodeId) {
            currentProgressFraction = progress
        }
        currentList.forEachIndexed { index, item ->
            if (item is EpisodeListItem.Episode && item.podcastEpisode.id == episodeId) {
                notifyItemChanged(index, progress)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is EpisodeListItem.LoadingMore -> VIEW_TYPE_LOADING_MORE
            is EpisodeListItem.Episode -> if (position == 0) VIEW_TYPE_LATEST else VIEW_TYPE_EPISODE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_LATEST -> {
                val binding = ItemEpisodeLatestBinding.inflate(inflater, parent, false)
                LatestViewHolder(binding)
            }
            VIEW_TYPE_EPISODE -> {
                val binding = ItemEpisodeBinding.inflate(inflater, parent, false)
                EpisodeViewHolder(binding)
            }
            VIEW_TYPE_LOADING_MORE -> {
                val binding = ItemPodcastLoadingMoreBinding.inflate(inflater, parent, false)
                LoadingMoreViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (item is EpisodeListItem.Episode) {
            val episode = item.podcastEpisode
            if (holder is LatestViewHolder) {
                holder.bind(episode)
            } else if (holder is EpisodeViewHolder) {
                holder.bind(episode)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val item = getItem(position)
            if (item is EpisodeListItem.Episode) {
                val episode = item.podcastEpisode
                payloads.forEach { payload ->
                    when (payload) {
                        PAYLOAD_PLAYING_STATE -> {
                            if (holder is LatestViewHolder) {
                                holder.updatePlayingState(episode)
                            } else if (holder is EpisodeViewHolder) {
                                holder.updatePlayingState(episode)
                            }
                        }
                        is Float -> {
                            if (holder is LatestViewHolder) {
                                holder.updateProgress(payload, episode)
                            } else if (holder is EpisodeViewHolder) {
                                holder.updateProgress(payload, episode)
                            }
                        }
                    }
                }
            }
        }
    }

    class LoadingMoreViewHolder(binding: ItemPodcastLoadingMoreBinding) : RecyclerView.ViewHolder(binding.root)

    inner class LatestViewHolder(private val binding: ItemEpisodeLatestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: PodcastEpisode) {
            val context = binding.root.context
            binding.episodeTitle.text = episode.title ?: episode.headLine
            binding.episodeTeaser.text = episode.teaser
            binding.episodeAuthors.text = constructAuthorsString(context, episode.authors)
            binding.episodeDate.text = dateFormat.format(episode.pubTime)
            binding.episodeDuration.text = context.getString(
                R.string.listen_podcast_episode_duration_minutes,
                episode.audio.playtime.toString()
            )

            bindPlayTimeLeft(binding.episodePlaytimeLeft, binding.episodePlaytimeLeftIcon, episode)

            binding.root.apply {
                setOnClickListener {
                    onPlayClicked(episode)
                }
                contentDescription = "Neueste Folge: ${getAccessibilityDescription(context, episode)}"
                ViewCompat.replaceAccessibilityAction(
                    this,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(R.string.listen_podcasts_play_episode_accessibility),
                    null
                )
            }

            updatePlayingState(episode)
        }

        fun updatePlayingState(episode: PodcastEpisode) {
            val isCurrent = episode.id == currentlyPlayingEpisodeId
            val iconRes = if (isCurrent && isPlaying) R.drawable.ic_pause_outline else R.drawable.ic_play_outline

            binding.playEpisodeButton.apply {
                (this as? MaterialButton)?.setIconResource(iconRes)
            }
        }
        fun updateProgress(progressFraction: Float, episode: PodcastEpisode) {
            bindPlayTimeLeft(binding.episodePlaytimeLeft, binding.episodePlaytimeLeftIcon, episode, progressFraction)
        }
    }

    inner class EpisodeViewHolder(private val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: PodcastEpisode) {
            val context = binding.root.context
            binding.episodeTitle.text = episode.title ?: episode.headLine
            binding.episodeTeaser.text = episode.teaser
            binding.episodeAuthors.text = episode.authors
            binding.episodeDate.text = dateFormat.format(episode.pubTime)
            binding.episodeDuration.text = context.getString(
                R.string.listen_podcast_episode_duration_minutes,
                episode.audio.playtime.toString()
            )
            bindPlayTimeLeft(binding.episodePlaytimeLeft, binding.episodePlaytimeLeftIcon, episode)
            binding.audioProgress.apply {
                max = CIRCULAR_PROGRESS_TICKS.toInt()
                progress = 0
                isIndeterminate = false
                val progressToUse = if (episode.id == currentlyPlayingEpisodeId) currentProgressFraction else episode.alreadyPlayed
                progress = (progressToUse * CIRCULAR_PROGRESS_TICKS).toInt()
            }
            binding.root.apply {
                setOnClickListener {
                    onPlayClicked(episode)
                }
                contentDescription = getAccessibilityDescription(context, episode)
                ViewCompat.replaceAccessibilityAction(
                    this,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(R.string.listen_podcasts_play_episode_accessibility),
                    null
                )
            }

            updatePlayingState(episode)
        }

        fun updateProgress(progressFraction: Float, episode: PodcastEpisode) {
            binding.audioProgress.progress = (progressFraction * CIRCULAR_PROGRESS_TICKS).toInt()
            bindPlayTimeLeft(binding.episodePlaytimeLeft, binding.episodePlaytimeLeftIcon, episode, progressFraction)
            binding.root.contentDescription = getAccessibilityDescription(binding.root.context, episode)
        }

        fun updatePlayingState(episode: PodcastEpisode) {
            val isCurrent = episode.id == currentlyPlayingEpisodeId

            binding.playEpisodePlayIcon.isInvisible = isCurrent && isPlaying
            binding.playEpisodePauseIcon.isVisible = isCurrent && isPlaying
        }
    }

    class EpisodeDiffCallback : DiffUtil.ItemCallback<EpisodeListItem>() {
        override fun areItemsTheSame(oldItem: EpisodeListItem, newItem: EpisodeListItem): Boolean {
            return if (oldItem is EpisodeListItem.Episode && newItem is EpisodeListItem.Episode) {
                oldItem.podcastEpisode.id == newItem.podcastEpisode.id
            } else {
                oldItem == newItem
            }
        }

        override fun areContentsTheSame(oldItem: EpisodeListItem, newItem: EpisodeListItem): Boolean {
            return oldItem == newItem
        }
    }

    private fun getAccessibilityDescription(context: Context, episode: PodcastEpisode): String {
        val title = episode.title ?: episode.headLine ?: ""
        val authors = episode.authors ?: ""
        val date = dateFormat.format(episode.pubTime)
        val duration = context.getString(
            R.string.listen_podcast_episode_duration_minutes,
            episode.audio.playtime.toString()
        )

        val progress = if (episode.id == currentlyPlayingEpisodeId) currentProgressFraction else episode.alreadyPlayed
        val status = when {
            progress >= MARK_AS_PLAYED_THRESHOLD -> context.getString(R.string.listen_podcast_episode_completed)
            progress > 0 -> {
                val secondsLeft = episode.audio.duration?.times(1 - progress)
                val minutesLeft = secondsLeft?.div(60)
                if (minutesLeft != null) {
                    if (minutesLeft > 1) {
                        context.getString(R.string.listen_podcast_playtime_left, minutesLeft.toInt().toString())
                    } else {
                        context.getString(R.string.listen_podcast_episode_less_then_a_minute)
                    }
                } else ""
            }
            else -> ""
        }

        return "$title, $authors, $date, $duration $status".trim()
    }

    private fun constructAuthorsString(context: Context, authorsString: String?): SpannableString {
        if (authorsString.isNullOrBlank()) return SpannableString("")

        val prefix = "Von "
        // replace last "," with " und"
        val lastCommaIndex = authorsString.lastIndexOf(",")
        val processedAuthors = if (lastCommaIndex != -1) {
            authorsString.substring(0, lastCommaIndex) + " und" + authorsString.substring(
                lastCommaIndex + 1
            )
        } else {
            authorsString
        }

        val result = "$prefix$processedAuthors"
        val spannable = SpannableString(result)
        //Widget.App.TextView.Listing.Author.Unbold
        val spanStyle = R.style.Widget_App_TextView_Listing_Author_Unbold

        // Style the "Von "
        spannable.setSpan(
            TextAppearanceSpan(context, spanStyle),
            0,
            prefix.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Style the " und"
        val undString = " und"
        val undIndex = result.lastIndexOf(undString)
        if (undIndex != -1 && lastCommaIndex != -1) {
            spannable.setSpan(
                TextAppearanceSpan(context, spanStyle),
                undIndex,
                undIndex + undString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    /**
     * Binds the playtime left to the episode item.
     * If the episode is already played, the playtime left is not shown.
     * If the episode is not yet played, nothing is shown.
     * If the episode is played more than [MARK_AS_PLAYED_THRESHOLD], it is shown as completed.
     * @param updatedProgressFraction [0,1] is given, we use that for binding instead of
     * episodfe.alreadyPlayed.
     */
    private fun bindPlayTimeLeft(
        textView: TextView,
        checkMark: TextView,
        episode: PodcastEpisode,
        updatedProgressFraction: Float? = null
    ) {
        val progress = updatedProgressFraction ?: episode.alreadyPlayed
        if (progress > 0 && progress < MARK_AS_PLAYED_THRESHOLD) {
            checkMark.isVisible = false
            textView.apply {
                isVisible = true
                val secondsLeft = episode.audio.duration?.times(1 - progress)
                val minutesLeft = secondsLeft?.div(60)
                if (minutesLeft == null) {
                    textView.isVisible = false
                    return@apply
                }
                text = if (minutesLeft > 1) {
                    context.getString(
                        R.string.listen_podcast_playtime_left,
                        minutesLeft.toInt()
                            .toString()
                    )
                } else {
                    context.getString(R.string.listen_podcast_episode_less_then_a_minute)
                }
            }
        } else if (progress >= MARK_AS_PLAYED_THRESHOLD) {
            textView.apply {
                isVisible = true
                text = context.getString(R.string.listen_podcast_episode_completed)
            }
            checkMark.isVisible = true
        } else {
            textView.isVisible = false
            checkMark.isVisible = false
        }
    }
}
