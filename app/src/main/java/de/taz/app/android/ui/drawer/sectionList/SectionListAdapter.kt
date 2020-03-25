package de.taz.app.android.ui.drawer.sectionList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.singletons.FileHelper
import kotlinx.coroutines.*
import java.util.*


class SectionListAdapter(
    private val fragment: SectionDrawerFragment,
    private var issueOperations: IssueOperations? = null
) : RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {

    private val fileHelper = FileHelper.getInstance()
    private val issueRepository = IssueRepository.getInstance()
    private val momentRepository = MomentRepository.getInstance()
    private val sectionRepository = SectionRepository.getInstance()

    private var moment: Moment? = null
    private val sectionList = mutableListOf<SectionStub>()
    private var imprint: ArticleStub? = null

    private var currentJob: Job? = null
    private val observer = MomentDownloadedObserver()

    fun setData(newIssueOperations: IssueOperations?) {
        if (issueOperations?.tag != newIssueOperations?.tag) {
            this.issueOperations = newIssueOperations

            moment?.isDownloadedLiveData()?.removeObserver(observer)
            fragment.view?.findViewById<MomentView>(
                R.id.fragment_drawer_sections_moment
            )?.apply {
                visibility = View.INVISIBLE
            }

            sectionList.clear()
            moment = null
            imprint = null

            drawIssue()
        }
    }

    private fun drawIssue() {
        currentJob?.cancel()
        currentJob = fragment.lifecycleScope.launch(Dispatchers.IO) {
            issueOperations?.let { issueStub ->

                moment = momentRepository.get(issueStub)
                sectionList.addAll(
                    sectionRepository.getSectionStubsForIssueOperations(
                        issueStub
                    )
                )
                imprint = issueRepository.getImprintStub(issueStub)
            }

            withContext(Dispatchers.Main) {
                imprint?.let(::showImprint)

                fragment.getMainView()?.apply {
                    moment?.isDownloadedLiveData()?.observe(
                        getLifecycleOwner(),
                        observer
                    )
                }
                notifyDataSetChanged()
            }
        }
    }

    private fun showImprint(imprint: ArticleOperations) {
        fragment.view?.findViewById<TextView>(
            R.id.fragment_drawer_sections_imprint
        )?.apply {
            text = text.toString().toLowerCase(Locale.getDefault())
            setOnClickListener {
                fragment.getMainView()?.apply {
                    showInWebView(imprint.key)
                    closeDrawer()
                }
            }
            visibility = View.VISIBLE
        }
    }


    inner class MomentDownloadedObserver : androidx.lifecycle.Observer<Boolean> {
        override fun onChanged(isDownloaded: Boolean?) {
            if (isDownloaded == true) {
                issueOperations?.let { issueOperations ->
                    moment?.isDownloadedLiveData()?.removeObserver(this)
                    fragment.view?.findViewById<MomentView>(
                        R.id.fragment_drawer_sections_moment
                    )?.apply {
                        displayIssue(issueOperations)
                        visibility = View.VISIBLE
                    }
                    setMomentDate(issueOperations)
                }
            }
        }
    }

    class SectionListAdapterViewHolder(val textView: TextView) :
        RecyclerView.ViewHolder(textView)

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
        val sectionStub = sectionList[position]
        sectionStub.let {
            holder.textView.text = sectionStub.title
            holder.textView.setOnClickListener {
                fragment.getMainView()?.apply {
                    lifecycleScope.launch(Dispatchers.IO) {
                        showInWebView(sectionStub.key)
                        withContext(Dispatchers.Main) {
                            closeDrawer()
                        }
                    }
                }
            }
        }
    }

    private fun setMomentDate(issueOperations: IssueOperations) {
        val dateHelper = DateHelper.getInstance()
        fragment.view?.findViewById<TextView>(
            R.id.fragment_drawer_sections_date
        )?.apply {
            text = dateHelper.stringToLongLocalizedString(issueOperations.date)
        }
    }

    override fun getItemCount() = sectionList.size
}
