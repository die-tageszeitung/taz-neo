package de.taz.app.android.ui.drawer.sectionList

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.ui.archive.item.ArchiveItemView
import de.taz.app.android.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class SectionListAdapter(
    private val fragment: SectionDrawerFragment,
    private var issue: Issue? = null
) : RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {

    private val fileHelper = FileHelper.getInstance()
    private val feedRepository = FeedRepository.getInstance()

    fun setData(newIssue: Issue?) {
        this.issue = newIssue
        newIssue?.let { issue ->
            downloadIssue(issue)

            fragment.getMainView()?.apply {
                issue.moment.isDownloadedLiveData().observe(
                    getLifecycleOwner(),
                    MomentDownloadedObserver()
                )
            }

            issue.imprint?.let { showImprint(it) }
            notifyDataSetChanged()
        }
    }

    private fun downloadIssue(issue: Issue) {
        fragment.getMainView()?.apply {
            lifecycleScope.launch(Dispatchers.IO) {
                if (!issue.moment.isDownloaded()) {
                    issue.downloadMoment(applicationContext)
                }
            }
        }
    }

    private fun showImprint(imprint: Article) {
        fragment.view?.findViewById<TextView>(
            R.id.fragment_drawer_sections_imprint
        )?.apply {
            text = text.toString().toLowerCase(Locale.getDefault())
            setOnClickListener {
                fragment.getMainView()?.apply {
                    showInWebView(imprint)
                    closeDrawer()
                }
            }
            visibility = View.VISIBLE
        }
    }


    inner class MomentDownloadedObserver : androidx.lifecycle.Observer<Boolean> {
        override fun onChanged(isDownloaded: Boolean?) {
            if (isDownloaded == true) {
                issue?.let { issue ->
                    issue.moment.isDownloadedLiveData().removeObserver(this)
                    setMomentRatio(issue)
                    setMomentImage(issue)
                }
            }
        }
    }

    class SectionListAdapterViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SectionListAdapterViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_drawer_sections_item, parent, false) as TextView
        return SectionListAdapterViewHolder(
            textView
        )
    }

    override fun onBindViewHolder(holder: SectionListAdapterViewHolder, position: Int) {
        val section = issue?.sectionList?.get(position)
        section?.let {
            holder.textView.text = section.title
            holder.textView.setOnClickListener {
                fragment.getMainView()?.apply {
                    showInWebView(section)
                    closeDrawer()
                }
            }
        }
    }

    private fun setMomentImage(issue: Issue) {
        issue.moment.imageList.lastOrNull()?.let {
            val imgFile = fileHelper.getFile("${issue.tag}/${it.name}")
            if (imgFile.exists()) {
                val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                fragment.view?.findViewById<ArchiveItemView>(
                    R.id.fragment_drawer_sections_moment
                )?.displayIssue(myBitmap, issue.date)
            }
        }
    }

    private fun setMomentRatio(issue: Issue) {
        fragment.lifecycleScope.launch(Dispatchers.IO) {
            val feed = feedRepository.get(issue.feedName)
            withContext(Dispatchers.Main) {
                fragment.view?.findViewById<ArchiveItemView>(
                    R.id.fragment_drawer_sections_moment
                )?.setDimension(feed)

            }
        }
    }

    override fun getItemCount() = issue?.sectionList?.size ?: 0
}
