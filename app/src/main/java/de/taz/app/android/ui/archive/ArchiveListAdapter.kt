package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.FeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ArchiveListAdapter(private val archiveFragment: ArchiveFragment) : BaseAdapter() {

    private var issueStubList: List<IssueStub> = emptyList()
    private val issueMomentBitmapMap: MutableMap<String, Bitmap> = mutableMapOf()

    private val feeds: Map<String, Feed> = runBlocking(Dispatchers.IO) {
        FeedRepository.getInstance().getAll().associateBy { it.name }
    }

    override fun getCount(): Int {
        return issueStubList.size
    }

    override fun getItem(position: Int): IssueStub {
        return issueStubList[position]
    }

    override fun getItemId(position: Int): Long {
        return 0L //TODO?
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // inflate the layout for each list row
        val view = convertView
            ?: LayoutInflater.from(archiveFragment.getMainView()?.getApplicationContext()).inflate(
                R.layout.fragment_archive_item, parent, false
            )

        val issueStub = getItem(position)

        view.tag = issueStub.tag

        setImageRatio(view, issueStub)

        val bitmap = issueMomentBitmapMap[issueStub.tag]

        if (bitmap != null) {
            view.findViewById<ImageView>(R.id.fragment_archive_moment_image).apply {
                setImageBitmap(bitmap)
                visibility = View.VISIBLE
            }
            view.findViewById<View>(R.id.fragment_archive_moment_image_progressbar).visibility =
                View.GONE
        } else {
            view.findViewById<ImageView>(R.id.fragment_archive_moment_image).visibility = View.GONE
            view.findViewById<View>(R.id.fragment_archive_moment_image_progressbar).visibility =
                View.VISIBLE
        }

        view.findViewById<TextView>(R.id.fragment_archive_moment_date).text = issueStub.date

        return view
    }

    private fun setImageRatio(view: View, issueStub: IssueStub) {
        view.findViewById<View>(R.id.fragment_archive_moment_image_wrapper).apply {
            (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio =
                feeds[issueStub.feedName]?.momentRatioAsDimensionRatioString()
        }
    }

    fun addMomentBitmap(tag: String, bitmap: Bitmap) {
        issueMomentBitmapMap[tag] = bitmap
    }

    fun setIssueStubs(issues: List<IssueStub>) {
        issueStubList = issues
        notifyDataSetChanged()
    }

}