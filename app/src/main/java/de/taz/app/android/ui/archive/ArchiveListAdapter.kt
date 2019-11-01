package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveListAdapter(private val archiveFragment: ArchiveFragment) : BaseAdapter() {

    private val log by Log

    private val momentRepository = MomentRepository.getInstance(
        archiveFragment.getMainView()?.getApplicationContext()
    )
    private val fileHelper = FileHelper.getInstance(
        archiveFragment.getMainView()?.getApplicationContext()
    )

    private var issueStubList: List<IssueStub> = emptyList()
    private val issueMomentBitmapMap: MutableMap<String, Bitmap> = mutableMapOf()


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

        issueMomentBitmapMap[issueStub.tag]?.let {
            view.findViewById<ImageView>(R.id.fragment_archive_moment_image).apply {
                setImageBitmap(it)
                visibility = View.VISIBLE
            }
            view.findViewById<View>(R.id.fragment_archive_moment_image_progressbar).visibility =
                View.GONE

        }
        view.findViewById<TextView>(R.id.fragment_archive_moment_date).text = issueStub.date

        return view
    }

    fun updateMomentList(issues: List<IssueStub>) {
        issueStubList = issues

        issues.forEach { issueStub ->
            if (!issueMomentBitmapMap.containsKey(issueStub.tag)) {
                generateMomentBitmapForIssueStub(issueStub)
            }
        }
        notifyDataSetChanged()
    }

    private fun generateMomentBitmapForIssueStub(issueStub: IssueStub) {
        archiveFragment.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
            momentRepository.get(issueStub)?.let { moment ->
                val observer = object : Observer<Boolean> {
                    override fun onChanged(isDownloaded: Boolean?) {
                        log.debug("issue: ${issueStub.tag} is downloaded? $isDownloaded")
                        if (isDownloaded == true) {
                            generateBitMapForMoment(issueStub, moment)
                            moment.isDownloadedLiveData().removeObserver(this)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    moment.isDownloadedLiveData().observe(
                        archiveFragment.getLifecycleOwner(),
                        observer
                    )
                }
            }
        }
    }

    private fun generateBitMapForMoment(issueStub: IssueStub, moment: Moment) {
        moment.imageList.lastOrNull()?.let {
            val imgFile = fileHelper.getFile("${issueStub.tag}/${it.name}")
            if (imgFile.exists()) {
                issueMomentBitmapMap[issueStub.tag] = BitmapFactory.decodeFile(imgFile.absolutePath)
                redrawViews()
            }
        }
    }

    private fun redrawViews() {
        archiveFragment.view?.findViewById<GridView>(R.id.fragment_archive_grid)?.invalidateViews()
    }

}