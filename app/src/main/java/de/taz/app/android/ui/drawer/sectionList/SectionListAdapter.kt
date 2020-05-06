package de.taz.app.android.ui.drawer.sectionList

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.moment.MomentView
import kotlinx.coroutines.*
import java.util.*


class SectionListAdapter(
    private val fragment: SectionDrawerFragment,
    private var issueOperations: IssueOperations? = null
) : RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {

    private val issueRepository =
        IssueRepository.getInstance(fragment.context?.applicationContext)
    private val momentRepository =
        MomentRepository.getInstance(fragment.context?.applicationContext)
    private val sectionRepository =
        SectionRepository.getInstance(fragment.context?.applicationContext)

    var activePosition = RecyclerView.NO_POSITION
        set(value) = run {
            val oldValue = field
            field = value
            if (value >= 0) {
                notifyItemChanged(value)
                if (oldValue >= 0) {
                    notifyItemChanged(oldValue)
                }
            }
        }

    private var moment: Moment? = null
    private val sectionList = mutableListOf<SectionStub>()
    private var imprint: ArticleStub? = null

    private var currentJob: Job? = null
    private val observer = MomentDownloadedObserver()

    fun setIssueOperations(newIssueOperations: IssueOperations?) {
        fragment.activity?.runOnUiThread {
            if (issueOperations?.tag != newIssueOperations?.tag) {
                fragment.view?.alpha = 0f
                this.issueOperations = newIssueOperations

                activePosition = RecyclerView.NO_POSITION

                sectionList.clear()
                moment = null
                imprint = null
            }
        }
    }

    fun getIssueOperations(): IssueOperations? {
        return issueOperations
    }

    fun show() {
        fragment.activity?.runOnUiThread {
            if (sectionList.isEmpty()) {
                drawIssue()
            }

            fragment.view?.scrollY = 0
            fragment.view?.animate()?.alpha(1f)?.duration = 500
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
                fragment.activity?.runOnUiThread {
                    notifyDataSetChanged()
                }
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
            fragment.lifecycleScope.launch(Dispatchers.Main) {
                typeface = if (issueOperations?.isWeekend == true) {
                    FontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                } else Typeface.create("aktiv_grotesk_bold", Typeface.BOLD)
            }
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
        CoroutineScope(Dispatchers.Main).launch {
            textView.typeface = if (issueOperations?.isWeekend == true) {
                FontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
            } else Typeface.create("aktiv_grotesk_bold", Typeface.BOLD)
        }
        return SectionListAdapterViewHolder(
            textView
        )
    }

    override fun onBindViewHolder(holder: SectionListAdapterViewHolder, position: Int) {
        val sectionStub = sectionList[position]
        sectionStub.let {

            holder.textView.apply {
                text = sectionStub.title
                setOnClickListener {
                    fragment.getMainView()?.apply {
                        lifecycleScope.launch(Dispatchers.IO) {
                            showInWebView(sectionStub.key)
                            withContext(Dispatchers.Main) {
                                closeDrawer()
                            }
                        }
                    }
                }
                if (position == activePosition) {
                    setTextColor(
                        ResourcesCompat.getColor(
                            resources,
                            R.color.drawer_sections_item_highlighted,
                            fragment.activity?.theme
                        )
                    )
                } else {
                    setTextColor(
                        ResourcesCompat.getColor(
                            resources,
                            R.color.drawer_sections_item,
                            fragment.activity?.theme
                        )
                    )
                }
            }
        }
    }

    override fun onViewRecycled(holder: SectionListAdapterViewHolder) {
        super.onViewRecycled(holder)
        CoroutineScope(Dispatchers.Main).launch {
            holder.textView.typeface = if (issueOperations?.isWeekend == true) {
                FontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
            } else Typeface.create("aktiv_grotesk_bold", Typeface.BOLD)
        }
    }

    private fun setMomentDate(issueOperations: IssueOperations) {
        val dateHelper = DateHelper.getInstance(fragment.context?.applicationContext)
        fragment.view?.findViewById<TextView>(
            R.id.fragment_drawer_sections_date
        )?.apply {
            text = dateHelper.stringToLongLocalizedString(issueOperations.date)
        }
    }

    override fun getItemCount() = sectionList.size

    fun positionOf(sectionFileName: String): Int {
        return sectionList.indexOfFirst { it.sectionFileName == sectionFileName }
    }

}
