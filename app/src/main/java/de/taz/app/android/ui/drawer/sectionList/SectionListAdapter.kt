package de.taz.app.android.ui.drawer.sectionList

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import de.taz.app.android.api.models.Issue
import de.taz.app.android.util.FileHelper


class SectionListAdapter(private val activity: MainActivity, private var issue: Issue? = null) :
    RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {

    private val fileHelper = FileHelper.getInstance(activity.applicationContext)

    fun setData(newIssue: Issue?) {
        this.issue = newIssue
        issue?.let { issue ->
            issue.moment.imageList.lastOrNull()?.let {
                val imgFile = fileHelper.getFile("${issue.tag}/${it.name}")

                if (imgFile.exists()) {
                    val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    activity.findViewById<ImageView>(R.id.drawer_moment)?.setImageBitmap(myBitmap)
                }
            }
            notifyDataSetChanged()
        }
    }

    class SectionListAdapterViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SectionListAdapterViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_drawer_menu_sections_item, parent, false) as TextView
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
                activity.closeDrawer()
            }
        }
    }

    override fun getItemCount() = issue?.sectionList?.size ?: 0
}
