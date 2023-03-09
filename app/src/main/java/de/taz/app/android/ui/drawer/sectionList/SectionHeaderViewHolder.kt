package de.taz.app.android.ui.drawer.sectionList

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Section

class SectionHeaderViewHolder(
    parent: ViewGroup,
    private val onTitleClickListener: (Section) -> Unit,
    private val onCollapseClickListener: (Section) -> Unit
) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_drawer_sections_item,
            parent,
            false
        )
    ) {

    private val sectionTitle: TextView = itemView.findViewById(R.id.fragment_drawer_section_title)
    private val toggleIcon: ImageView = itemView.findViewById(R.id.fragment_drawer_section_collapse_icon)
    private val toggleSeparator: ImageView = itemView.findViewById(R.id.dotted_separator)
    private val toggleWrapper: LinearLayout = itemView.findViewById(R.id.section_toggle_wrapper)

    fun bind(header: SectionDrawerItem.Header, typeface: Typeface? = null) {
        typeface?.let { sectionTitle.typeface = it }
        sectionTitle.text = header.section.title

        sectionTitle.setOnClickListener {
            onTitleClickListener(header.section)
        }

        if (header.isExpanded) {
            toggleIcon.rotation = 180f
        } else {
            toggleIcon.rotation = 0f
        }

        if (header.section.articleList.isNotEmpty()) {
            toggleWrapper.setOnClickListener {
                onCollapseClickListener(header.section)
            }
        } else {
            // if the sections have no articles, let the arrow indicate that (eg "anzeige")
            // (and open the section clicking on the arrow)
            toggleIcon.rotation = 270f
            toggleSeparator.visibility = View.GONE
            toggleWrapper.setOnClickListener {
                onTitleClickListener(header.section)
            }
        }
    }
}