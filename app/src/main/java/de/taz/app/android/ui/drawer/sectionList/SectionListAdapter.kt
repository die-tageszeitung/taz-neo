package de.taz.app.android.ui.drawer.sectionList

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import de.taz.app.android.api.models.Issue


class SectionListAdapter(private val activity: MainActivity, private var issue: Issue? = null) :
    RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {

    fun setData(newIssue: Issue?) {
        this.issue = newIssue
        notifyDataSetChanged()
    }

    class SectionListAdapterViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SectionListAdapterViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_drawer_menu_list_item, parent, false) as TextView
        return SectionListAdapterViewHolder(
            textView
        )
    }

    override fun onBindViewHolder(holder: SectionListAdapterViewHolder, position: Int) {
        val section = issue?.sectionList?.get(position)
        section?.let {
            holder.textView.text = section.title
            holder.textView.setOnClickListener {
                activity.showSection(section)
            }
        }
    }

    override fun getItemCount() = issue?.sectionList?.size ?: 0
}
