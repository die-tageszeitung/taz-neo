package de.taz.app.android.ui.drawer.sectionList

import android.content.res.Resources
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import kotlinx.android.synthetic.main.fragment_drawer_sections_item.view.*


class SectionListAdapter(
    private val onSectionClickerListener: (SectionStub) -> Unit,
    private val theme: Resources.Theme?
) : RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {
    var sectionList: List<SectionStub> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var typeface: Typeface? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var activePosition = RecyclerView.NO_POSITION
        set(value) {
            val oldValue = field
            field = value
            if (value >= 0 && sectionList.size > value) {
                notifyItemChanged(value)
            }
            if (oldValue >= 0 && sectionList.size > value) {
                notifyItemChanged(oldValue)
            }
        }

    class SectionListAdapterViewHolder(val sectionTitleItem: View) : RecyclerView.ViewHolder(sectionTitleItem)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SectionListAdapterViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_drawer_sections_item, parent, false)
        return SectionListAdapterViewHolder(
            textView
        )
    }

    override fun onBindViewHolder(holder: SectionListAdapterViewHolder, position: Int) {
        if(position != RecyclerView.NO_POSITION){
            val sectionStub = sectionList[position]
            sectionStub.let {
                holder.sectionTitleItem.fragment_drawer_section_title.apply {
                    typeface = this@SectionListAdapter.typeface
                    text = sectionStub.title
                    setOnClickListener {
                        onSectionClickerListener(sectionStub)
                    }
                    if (position == activePosition) {
                        setTextColor(
                            ResourcesCompat.getColor(
                                resources,
                                R.color.drawer_sections_item_highlighted,
                                theme
                            )
                        )
                    } else {
                        setTextColor(
                            ResourcesCompat.getColor(
                                resources,
                                R.color.drawer_sections_item,
                                theme
                            )
                        )
                    }
                }
            }
        }

    }

    override fun onViewRecycled(holder: SectionListAdapterViewHolder) {
        holder.sectionTitleItem.fragment_drawer_section_title.typeface = typeface
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = sectionList.size

    fun positionOf(sectionFileName: String): Int? {
        return sectionList.indexOfFirst { it.sectionFileName == sectionFileName }
    }

}
