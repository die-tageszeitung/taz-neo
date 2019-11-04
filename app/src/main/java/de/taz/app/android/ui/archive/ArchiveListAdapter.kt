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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ArchiveListAdapter(private val archiveFragment: ArchiveFragment) : BaseAdapter() {

    private val momentRepository = MomentRepository.getInstance(
        archiveFragment.getMainView()?.getApplicationContext()
    )

    private var issueStubList: List<IssueStub> = emptyList()
    val issueMomentBitmapMap: MutableMap<String, Bitmap> = mutableMapOf()
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
            archiveFragment.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                momentRepository.get(issueStub)?.let { moment ->
                    if (!moment.isDownloadedOrDownloading()) {
                        archiveFragment.getMainView()?.getApplicationContext()?.let {
                            DownloadService.download(it, moment)
                        }
                    }
                }
            }

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
                val observer = ArchiveMomentDownloadObserver(
                    archiveFragment,
                    this@ArchiveListAdapter,
                    issueStub,
                    moment
                )

                withContext(Dispatchers.Main) {
                    moment.isDownloadedLiveData().observe(
                        archiveFragment.getLifecycleOwner(),
                        observer
                    )
                }
            }
        }
    }

}

class ArchiveMomentDownloadObserver(
    private val archiveFragment: ArchiveFragment,
    private val archiveListAdapter: ArchiveListAdapter,
    private val issueStub: IssueStub,
    private val moment: Moment
) : Observer<Boolean> {

    private val log by Log

    private val fileHelper = FileHelper.getInstance(
        archiveFragment.getMainView()?.getApplicationContext()
    )

    override fun onChanged(isDownloaded: Boolean?) {
        archiveFragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            log.debug("issue: ${issueStub.tag} is downloaded? $isDownloaded")
            if (isDownloaded == true) {
                generateBitMapForMoment(issueStub, moment)
                withContext(Dispatchers.Main) {
                    moment.isDownloadedLiveData().removeObserver(this@ArchiveMomentDownloadObserver)
                }
            }
        }
    }

    private suspend fun generateBitMapForMoment(issueStub: IssueStub, moment: Moment) {
        moment.imageList.lastOrNull()?.let {
            fileHelper.getFile("${issueStub.tag}/${it.name}").let { imgFile ->
                if (imgFile.exists()) {
                    archiveListAdapter.issueMomentBitmapMap[issueStub.tag] =
                        BitmapFactory.decodeFile(imgFile.absolutePath)
                    redrawViews()
                }
            }
        }
    }

    private suspend fun redrawViews() {
        // TODO improve performance…
        withContext(Dispatchers.Main) {
            archiveFragment.view?.findViewById<GridView>(R.id.fragment_archive_grid)
                ?.invalidateViews()
        }
    }

}