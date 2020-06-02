package de.taz.app.android.ui.home.feedFilter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.ViewModelFragment
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.FeedHelper
import kotlinx.android.synthetic.main.fragment_archive_end_navigation.*

class FeedFilterFragment :
    ViewModelFragment<FeedFilterViewModel>(R.layout.fragment_archive_end_navigation) {

    internal val adapter = FeedAdapter()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var feedHelper: FeedHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_archive_navigation_end_feed_list.adapter = adapter

        context?.let { context ->
            fragment_archive_navigation_end_feed_list.layoutManager =
                LinearLayoutManager(context)
        }
        viewModel.feedsLiveData.observeDistinct(this) { feeds ->
            setFeeds(feeds)
        }
        viewModel.inactiveFeedNameLiveData.observeDistinct(this) { inactiveFeedNames ->
            setInactiveFeedNames(inactiveFeedNames)
        }

        feedHelper = FeedHelper.getInstance(context?.applicationContext)
    }

    fun setFeeds(feeds: List<Feed>) {
        feeds.forEach { feed ->
            if (feed !in adapter.feedList) {
                adapter.feedList.add(feed)
            }
        }
        adapter.notifyDataSetChanged()
    }

    fun setInactiveFeedNames(inactiveFeedNames: Set<String>) {
        if (adapter.inactiveFeedList != inactiveFeedNames) {
            adapter.inactiveFeedList = inactiveFeedNames.toMutableList()
            adapter.notifyDataSetChanged()
        }
    }

    private fun setImageInactive(imageView: ImageView) {
        context?.let { context ->
            imageView.setColorFilter(
                ContextCompat.getColor(context, R.color.white),
                android.graphics.PorterDuff.Mode.SRC_OVER
            )
        }
    }

    private fun setImageActive(imageView: ImageView) {
        context?.let { context ->
            imageView.setColorFilter(
                ContextCompat.getColor(context, R.color.colorAccent),
                android.graphics.PorterDuff.Mode.SRC_OVER
            )
        }
    }

    inner class FeedAdapter : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {

        var inactiveFeedList = mutableListOf<String>()
        val feedList = mutableListOf<Feed>()

        override fun getItemCount(): Int {
            return feedList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(activity)
                .inflate(R.layout.fragment_archive_end_navigation_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val feed = feedList[position]

            if (feed.name in inactiveFeedList) {
                setImageInactive(holder.imageView)
            } else {
                setImageActive(holder.imageView)
            }

            holder.textView.text = feed.name
        }


        /**
         * ViewHolder for this Adapter
         */
        inner class ViewHolder constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView =
                itemView.findViewById(R.id.fragment_archive_navigation_end_item_text)
            val imageView: ImageView =
                itemView.findViewById(R.id.fragment_archive_navigation_end_item_image)

            init {
                itemView.setOnClickListener {
                    val feed = feedList[adapterPosition]
                    onFeedClicked(feed)
                    if (feed.name !in inactiveFeedList) {
                        setImageInactive(imageView)
                    } else {
                        setImageActive(imageView)
                    }
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun onFeedClicked(feed: Feed) {
        val inactiveFeedNames = viewModel.getInactiveFeedNames()
        if (feed.name !in inactiveFeedNames) {
            feedHelper.deactivateFeed(feed)
        } else {
            feedHelper.activateFeed(feed)
        }
    }
}
