package de.taz.app.android.ui.archive

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.api.models.Moment

class ArchiveListAdapter(val context: Context): BaseAdapter() {

    private var momentList: List<Moment> = emptyList()

    override fun getCount(): Int {
        return momentList.size
    }

    override fun getItem(position: Int): Moment {
        return momentList[position]
    }

    override fun getItemId(position: Int): Long {
        return 0L //TODO?
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // inflate the layout for each list row
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.fragment_archive_moment, parent, false)

        val item = getItem(position)

        view.findViewById<ImageView>(R.id.fragment_archive_moment_image)

        view.findViewById<TextView>(R.id.fragment_archive_moment_date).text = item.getSectionStub().date

    }

}