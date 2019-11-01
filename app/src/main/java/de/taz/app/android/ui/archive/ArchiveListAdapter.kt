package de.taz.app.android.ui.archive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ArchiveListAdapter(val context: Context): BaseAdapter() {

    private var issueStubList: List<IssueStub> = emptyList()
    private var momentList: List<Moment?> = emptyList()
    private var bitmapList: List<Bitmap?> = emptyList()

    private val fileHelper = FileHelper.getInstance(context.applicationContext)
    private val momentRepository = MomentRepository.getInstance(context.applicationContext)

    override fun getCount(): Int {
        return issueStubList.size
    }

    override fun getItem(position: Int): IssueStub{
        return issueStubList[position]
    }

    override fun getItemId(position: Int): Long {
        return 0L //TODO?
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // inflate the layout for each list row
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.fragment_archive_item, parent, false)

        val issueStub = getItem(position)


        view.findViewById<ImageView>(R.id.fragment_archive_moment_image).setImageBitmap(bitmapList[position])
        view.findViewById<TextView>(R.id.fragment_archive_moment_date).text = issueStub.date

        return view
    }

    fun updateMomentList(issues: List<IssueStub>) {
        issueStubList = issues
        runBlocking(Dispatchers.IO) {
            momentList = issues.map { momentRepository.get(it) }
        }
        bitmapList = momentList.map { it?.let { bitmapFromMoment(it) } }

        notifyDataSetChanged()
    }

    private fun bitmapFromMoment(moment: Moment): Bitmap? {
        val position = momentList.indexOf(moment)
        val issueStub = issueStubList[position]

        moment.imageList.lastOrNull()?.let {
            val imgFile = fileHelper.getFile("${issueStub.tag}/${it.name}")
            if (imgFile.exists()) {
                return BitmapFactory.decodeFile(imgFile.absolutePath)
            }
        }
        return null
    }

}