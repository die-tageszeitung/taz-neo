package de.taz.app.android.ui.bookmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.singletons.DateHelper

class IssueOfBookmarkViewHolder(
    val parent: ViewGroup
) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_bookmarks_issue_header,
            parent,
            false
        )
    ) {
    fun bind(header: BookmarkListItem.Header) {
        itemView.findViewById<TextView>(R.id.fragment_bookmarks_issue_header_date)
            .text = header.dateString

        val momentImageView =
            itemView.findViewById<ImageView>(R.id.fragment_bookmarks_moment_image)

        Glide.with(parent.context.applicationContext)
            .load(header.momentImageUri)
            .into(momentImageView)
    }
}