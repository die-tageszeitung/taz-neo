package de.taz.app.android.ui.bookmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R

class IssueOfBookmarkViewHolder(
    val parent: ViewGroup,
    private val goToIssueInCoverFlow: (String) -> Unit,
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
            .text = header.localizedDateString

        val momentImageView =
            itemView.findViewById<ImageView>(R.id.fragment_bookmarks_moment_image)

        val layout =
            itemView.findViewById<ConstraintLayout>(R.id.fragment_bookmarks_issue_header_layout)

        layout.setOnClickListener {
            goToIssueInCoverFlow(header.dateString)
        }

        Glide.with(parent.context.applicationContext)
            .load(header.momentImageUri)
            .into(momentImageView)
    }
}