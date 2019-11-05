package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.DownloadRepository
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ArchiveListAdapter(
    private val archiveFragment: ArchiveFragment
) : RecyclerView.Adapter<ArchiveListAdapter.ViewHolder>() {

    private val log by Log

    private var issueStubList: List<IssueStub> = emptyList()
    private val issueMomentBitmapMap: MutableMap<String, Bitmap> = mutableMapOf()
    private val issueStubGenerationList: MutableList<String> = mutableListOf()

    private val feeds: Map<String, Feed> = runBlocking(Dispatchers.IO) {
        FeedRepository.getInstance().getAll().associateBy { it.name }
    }

    override fun getItemCount(): Int {
        return issueStubList.size
    }

    fun getItem(position: Int): IssueStub {
        return issueStubList[position]
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    private fun setImageRatio(view: View, issueStub: IssueStub) {
        (view.layoutParams as? ConstraintLayout.LayoutParams)?.dimensionRatio =
            feeds[issueStub.feedName]?.momentRatioAsDimensionRatioString()
    }

    fun addMomentBitmaps(map: Map<String, Bitmap>) {
        issueMomentBitmapMap.putAll(map)
    }

    fun addMomentBitmap(tag: String, bitmap: Bitmap) {
        issueMomentBitmapMap[tag] = bitmap
        notifyItemChanged(issueStubList.indexOfFirst { it.tag == tag }, bitmap)
    }

    fun setIssueStubs(issues: List<IssueStub>) {
        issueStubList = issues
        notifyDataSetChanged()
    }

    // inflates the cell layout from xml when needed
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(archiveFragment.getMainView()?.getApplicationContext())
            .inflate(R.layout.fragment_archive_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.size == 1 && payloads.first() is Bitmap) {
            showImage(holder, payloads.first() as Bitmap)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }

    }

    // binds the data to the TextView in each cell
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.dateText

        val issueStub = getItem(position)

        setImageRatio(viewHolder.momentImageWrapper, issueStub)

        val bitmap = issueMomentBitmapMap[issueStub.tag]

        if (bitmap != null) {
            showImage(viewHolder, bitmap)
        } else {
            if (issueStub.tag !in issueStubGenerationList) {
                issueStubGenerationList.add(issueStub.tag)
                generateMomentBitmapForIssueStub(issueStub)
            }
            showProgressBar(viewHolder)
        }

        viewHolder.dateText.text = issueStub.date
    }

    private fun showProgressBar(viewHolder: ViewHolder) {
        viewHolder.momentImage.visibility = View.GONE
        viewHolder.progressBar.visibility = View.VISIBLE
    }

    private fun showImage(viewHolder: ViewHolder, bitmap: Bitmap) {
        viewHolder.momentImage.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        viewHolder.progressBar.visibility = View.GONE
    }

    // stores and recycles views as they are scrolled off screen
    inner class ViewHolder constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.fragment_archive_moment_date)
        val momentImage: ImageView =
            itemView.findViewById(R.id.fragment_archive_moment_image)
        val momentImageWrapper: ConstraintLayout =
            itemView.findViewById(R.id.fragment_archive_moment_image_wrapper)
        val progressBar: ProgressBar =
            itemView.findViewById(R.id.fragment_archive_moment_image_progressbar)

        init {
            itemView.setOnClickListener {
                archiveFragment.presenter.onItemSelected(getItem(adapterPosition))
            }
        }
    }

    private fun generateMomentBitmapForIssueStub(issueStub: IssueStub) {

        archiveFragment.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {

            MomentRepository.getInstance().get(issueStub)?.let { moment ->
                archiveFragment.getMainView()?.getApplicationContext()?.let { applicationContext ->
                    if (!moment.isDownloaded()) {
                        log.debug("requesting download of $moment")
                        DownloadService.download(applicationContext, moment)
                    }
                    val observer = ArchiveMomentDownloadObserver(archiveFragment, issueStub, moment)

                    withContext(Dispatchers.Main) {
                        archiveFragment.getLifecycleOwner().let {
                            moment.isDownloadedLiveData().observe(
                                it, observer
                            )
                        }
                    }
                }
            }
        }
    }

}


class ArchiveMomentDownloadObserver(
    private val archiveFragment: ArchiveFragment,
    private val issueStub: IssueStub,
    private val moment: Moment
) : Observer<Boolean> {

    private val log by Log

    override fun onChanged(isDownloaded: Boolean?) {
        if (isDownloaded == true) {
            log.debug("moment is download: $moment")
            moment.isDownloadedLiveData().removeObserver(this@ArchiveMomentDownloadObserver)
            archiveFragment.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                log.debug("generating image for $moment")
                generateBitMapForMoment(issueStub, moment)
            }
        } else {
            log.debug("waiting for not yet downloaded: $moment")
        }
    }

    private fun generateBitMapForMoment(issueStub: IssueStub, moment: Moment) {
        moment.imageList.lastOrNull()?.let {
            FileHelper.getInstance().getFile("${issueStub.tag}/${it.name}").let { imgFile ->
                if (imgFile.exists()) {
                    // cache it and show it
                    archiveFragment.presenter.onMomentBitmapCreated(
                        issueStub.tag,
                        BitmapFactory.decodeFile(imgFile.absolutePath)
                    )
                } else {
                    log.error("imgFile of $moment does not exist")
                }
            }
        }
    }

}