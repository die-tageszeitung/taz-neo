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
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 *  [ArchiveListAdapter] binds the [IssueStub]s from [ArchiveDataController] to the [RecyclerView]
 *  [ViewHolder] is used to recycle views
 */
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

    fun getItem(position: Int): IssueStub {
        return issueStubList[position]
    }

    override fun getItemCount(): Int {
        return issueStubList.size
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    fun getItemPosition(issueStub: IssueStub): Int {
        return issueStubList.indexOf(issueStub)
    }

    fun addBitmaps(map: Map<String, Bitmap>) {
        issueMomentBitmapMap.putAll(map)
    }

    fun addBitmap(tag: String, bitmap: Bitmap) {
        issueMomentBitmapMap[tag] = bitmap
        notifyItemChanged(issueStubList.indexOfFirst { it.tag == tag }, bitmap)
    }

    fun setIssueStubs(issues: List<IssueStub>) {
        if (issues.isNotEmpty() && issueStubList.isNotEmpty()) {
            val firstOldIndex = issueStubList.indexOf(issues.first())
            val lastOldIndex = issues.indexOf(issueStubList.last())

            issueStubList = issues

            if (firstOldIndex > 0) {
                notifyItemRangeInserted(0, firstOldIndex)
            }
            if (lastOldIndex < issues.size) {
                notifyItemRangeInserted(lastOldIndex + 1, issues.size - 1)
            }
        } else {
            issueStubList = issues
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(archiveFragment.getMainView()?.getApplicationContext())
            .inflate(R.layout.fragment_archive_item, parent, false)
        return ViewHolder(view)
    }

    /**
     * called if a payload is given/changes
     * used to draw the image without having to redraw the whole view
     * and show or hide progressbar
     */
    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.size == 1) {
            val payload: Any = payloads.first()
            when (payload) {
                is Bitmap ->
                    showBitmap(viewHolder, payload)
                is Boolean ->
                    if (payload) showProgressBar(viewHolder)
                    else hideProgressBar(viewHolder)
            }
        } else {
            super.onBindViewHolder(viewHolder, position, payloads)
        }
    }

    private fun showProgressBar(viewHolder: ViewHolder) {
        viewHolder.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar(viewHolder: ViewHolder) {
        viewHolder.progressBar.visibility = View.GONE
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.dateText

        val issueStub = getItem(position)

        setImageRatio(viewHolder.momentImageWrapper, issueStub)

        val bitmap = issueMomentBitmapMap[issueStub.tag]

        if (bitmap != null) {
            showBitmap(viewHolder, bitmap)
        } else {
            if (issueStub.tag !in issueStubGenerationList) {
                issueStubGenerationList.add(issueStub.tag)
                downloadMomentAndGenerateImage(issueStub)
            }
            showProgressBar(viewHolder)
            hideBitmap(viewHolder)
        }

        viewHolder.dateText.text = issueStub.date
    }

    private fun setImageRatio(view: View, issueStub: IssueStub) {
        (view.layoutParams as? ConstraintLayout.LayoutParams)?.dimensionRatio =
            feeds[issueStub.feedName]?.momentRatioAsDimensionRatioString()
    }

    private fun hideBitmap(viewHolder: ViewHolder) {
        viewHolder.momentImage.visibility = View.GONE
    }

    private fun showBitmap(viewHolder: ViewHolder, bitmap: Bitmap) {
        viewHolder.momentImage.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        hideProgressBar(viewHolder)
    }


    private fun downloadMomentAndGenerateImage(issueStub: IssueStub) {
        archiveFragment.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {

            val moment = MomentRepository.getInstance().get(issueStub)

            moment?.let {
                if (!moment.isDownloaded()) {
                    val applicationContext = archiveFragment.getMainView()?.getApplicationContext()

                    applicationContext?.let {
                        log.debug("requesting download of $moment")
                        DownloadService.download(applicationContext, moment)
                        val observer = ArchiveMomentDownloadObserver(
                                this@ArchiveListAdapter,
                                issueStub,
                                moment
                            )

                        withContext(Dispatchers.Main) {
                            archiveFragment.getLifecycleOwner().let {
                                moment.isDownloadedLiveData().observe(
                                    it, observer
                                )
                            }
                        }
                    }
                } else {
                    generateBitmapForMoment(issueStub, moment)
                }
            }
        }
    }

    private fun generateBitmapForMoment(issueStub: IssueStub, moment: Moment) {
        // get biggest image -> TODO save image resolution?
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

    /**
     * ViewHolder for this Adapter
     */
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

    /**
     * Observer to generate Images for a [Moment] once it's downloaded
     * @param archiveListAdapter
     */
    private class ArchiveMomentDownloadObserver(
        private val archiveListAdapter: ArchiveListAdapter,
        private val issueStub: IssueStub,
        private val moment: Moment
    ) : Observer<Boolean> {

        private val log by Log

        override fun onChanged(isDownloaded: Boolean?) {
            if (isDownloaded == true) {
                log.debug("moment is downloaded: $moment")
                moment.isDownloadedLiveData().removeObserver(this@ArchiveMomentDownloadObserver)
                archiveListAdapter.archiveFragment.getLifecycleOwner().lifecycleScope.launch(
                    Dispatchers.IO
                ) {
                    log.debug("generating image for $moment")
                    archiveListAdapter.generateBitmapForMoment(issueStub, moment)
                }
            } else {
                log.debug("waiting for not yet downloaded: $moment")
            }
        }

    }
}
