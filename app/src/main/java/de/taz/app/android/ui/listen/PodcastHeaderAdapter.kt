package de.taz.app.android.ui.listen

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE
import de.taz.app.android.R
import de.taz.app.android.api.models.Podcast
import de.taz.app.android.databinding.HeaderPodcastCarouselBinding
import de.taz.app.android.databinding.ItemPodcastCardBinding
import de.taz.app.android.singletons.StorageService
import kotlin.math.abs
import kotlin.math.max

/**
 * Adapter for the podcast header, which contains a horizontal carousel of podcasts.
 * This adapter only has a single item (the carousel container).
 */
class PodcastHeaderAdapter(
    private val onPodcastSelected: (Podcast) -> Unit
) : RecyclerView.Adapter<PodcastHeaderAdapter.ViewHolder>() {

    private var podcasts: List<Podcast> = emptyList()
    private var selectedPodcastId: Int? = null
    private val carouselAdapter = PodcastCarouselAdapter { _ ->
        // This will be overridden or called by the ViewHolder
    }

    fun submitList(newPodcasts: List<Podcast>) {
        podcasts = newPodcasts
        carouselAdapter.submitList(newPodcasts)
        notifyDataSetChanged()
    }

    fun setSelectedPodcast(podcastId: Int?) {
        if (selectedPodcastId == podcastId) return
        selectedPodcastId = podcastId
        carouselAdapter.setSelectedPodcast(podcastId)
        notifyItemChanged(podcastId ?: 0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = HeaderPodcastCarouselBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(selectedPodcastId)
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(private val binding: HeaderPodcastCarouselBinding) : RecyclerView.ViewHolder(binding.root) {
        private val snapHelper = GravitySnapHelper(Gravity.CENTER)

        init {
            binding.carouselRecyclerView.apply {
                val estimatedWidth = resources.getDimensionPixelSize(R.dimen.podcast_card_width) +
                        (2 * resources.getDimensionPixelSize(R.dimen.podcast_card_margin))
                
                layoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
                    override fun getPaddingLeft(): Int = getPadding()
                    override fun getPaddingRight(): Int = getPadding()

                    private fun getPadding(): Int {
                        val width = children.firstOrNull()?.measuredWidth ?: estimatedWidth
                        return if (width > 0) {
                            this@apply.width / 2 - width / 2
                        } else 0
                    }

                    override fun onLayoutCompleted(state: RecyclerView.State?) {
                        super.onLayoutCompleted(state)
                        adjustViewSizes()
                    }
                }

                adapter = carouselAdapter.apply {
                    // Set the listener to handle the smooth scroll using the local RecyclerView
                    onPodcastClicked = { position ->
                        smoothScrollToPosition(position)
                    }
                }

                snapHelper.attachToRecyclerView(this)
                
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val currentPos = snapHelper.currentSnappedPosition
                        updateUi(currentPos)
                        adjustViewSizes()
                    }
                })
            }

            binding.carouselGoPrevious.setOnClickListener {
                val currentPos = snapHelper.currentSnappedPosition
                if (currentPos > 0) {
                    binding.carouselRecyclerView.smoothScrollToPosition(currentPos - 1)
                }
            }

            binding.carouselGoNext.setOnClickListener {
                val currentPos = snapHelper.currentSnappedPosition
                if (currentPos < podcasts.size - 1) {
                    binding.carouselRecyclerView.smoothScrollToPosition(currentPos + 1)
                }
            }
        }

        fun bind(selectedId: Int?) {
            val position = podcasts.indexOfFirst { it.id == selectedId }
            if (position != -1) {
                val currentSnapped = snapHelper.currentSnappedPosition
                if (currentSnapped != position) {
                    val shouldSmoothScroll = currentSnapped != -1 &&
                            abs(position - currentSnapped) <= COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE
                    
                    binding.carouselRecyclerView.apply {
                        if (shouldSmoothScroll) {
                            smoothScrollToPosition(position)
                        } else {
                            scrollToPosition(position)
                        }
                    }
                }
            }
            val currentPos = snapHelper.currentSnappedPosition
            updateUi(currentPos)
        }

        private fun updateUi(position: Int) {
            updateChevrons(position)
            updateSelectedPodcast(position)
        }

        private fun updateChevrons(currentPos: Int) {
            if (currentPos > 0) {
                binding.carouselGoPrevious.visibility = View.VISIBLE
            } else {
                binding.carouselGoPrevious.visibility = View.INVISIBLE
            }
            if (currentPos != -1 && currentPos < podcasts.size - 1) {
                binding.carouselGoNext.visibility = View.VISIBLE
            } else {
                binding.carouselGoNext.visibility = View.INVISIBLE
            }
        }

        private fun updateSelectedPodcast(currentPos: Int) {
            if (currentPos != RecyclerView.NO_POSITION && currentPos < podcasts.size) {
                val podcast = podcasts[currentPos]
                binding.podcastName.text = podcast.displayName
                binding.podcastTitleDescription.text = podcast.title

                // Notify selection if the snapped podcast has changed and differs from the current selection
                if (podcast.id != selectedPodcastId) {
                    selectedPodcastId = podcast.id
                    onPodcastSelected(podcast)
                }
            }
        }

        private fun adjustViewSizes() {
            val recyclerView = binding.carouselRecyclerView
            val center = recyclerView.width / 2f
            val width = recyclerView.width.toFloat()
            if (width <= 0) return

            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val childCenter = (child.left + child.right) / 2f
                val position = (center - childCenter) / width

                val minScale = 0.85f
                val scaleFactor = max(minScale, 1f - (abs(position) * 0.5f))
                child.scaleX = scaleFactor
                child.scaleY = scaleFactor
            }
        }
    }
}

/**
 * Internal adapter for the horizontal carousel of podcasts.
 */
private class PodcastCarouselAdapter(
    var onPodcastClicked: (Int) -> Unit
) : ListAdapter<Podcast, PodcastCarouselAdapter.ViewHolder>(PodcastDiffCallback()) {

    private var selectedPodcastId: Int? = null

    fun setSelectedPodcast(podcastId: Int?) {
        if (selectedPodcastId == podcastId) return
        val oldSelectedId = selectedPodcastId
        selectedPodcastId = podcastId

        // Targeted updates to improve performance
        val list = currentList
        list.forEachIndexed { index, podcast ->
            if (podcast.id == oldSelectedId || podcast.id == podcastId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPodcastCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Data changed, but we don't want to reload the image if it's the same.
            // holder.bind(getItem(position)) would reload Glide.
            // If we have a payload, it means it's a data update (like progress).
            // We only re-bind if the image path actually changed.
            holder.updateIfImageChanged(getItem(position))
        }
    }

    inner class ViewHolder(private val binding: ItemPodcastCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private val storageService = StorageService.getInstance(binding.root.context.applicationContext)
        private var currentImagePath: String? = null

        fun bind(podcast: Podcast) {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return

            with(binding) {
                val episode = podcast.episodeList.firstOrNull()
                // Use latest episode icon, otherwise use default icon
                val previewImage = if (episode?.icon != null) {
                    episode.icon
                } else {
                    podcast.defaultIcon
                }

                val imagePath = previewImage.fileEntry?.let { storageService.getAbsolutePath(it) }
                
                // If it's the same image, don't trigger Glide at all to avoid flicker
                if (imagePath == currentImagePath && podcastImage.drawable != null) {
                    return@with
                }
                
                currentImagePath = imagePath

                Glide.with(root.context)
                    .load(imagePath)
                    .into(podcastImage)

                podcastImage.setOnClickListener {
                    onPodcastClicked(position)
                }
            }
        }

        fun updateIfImageChanged(podcast: Podcast) {
            val episode = podcast.episodeList.firstOrNull()
            val previewImage = if (episode?.icon != null) {
                episode.icon
            } else {
                podcast.defaultIcon
            }
            val imagePath = previewImage.fileEntry?.let { storageService.getAbsolutePath(it) }
            if (imagePath != currentImagePath) {
                bind(podcast)
            }
        }
    }

    private class PodcastDiffCallback : DiffUtil.ItemCallback<Podcast>() {
        override fun areItemsTheSame(oldItem: Podcast, newItem: Podcast): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Podcast, newItem: Podcast): Boolean = oldItem == newItem

        override fun getChangePayload(oldItem: Podcast, newItem: Podcast): Any? {
            if (oldItem.id == newItem.id) return "DATA_CHANGE"
            return super.getChangePayload(oldItem, newItem)
        }
    }
}
