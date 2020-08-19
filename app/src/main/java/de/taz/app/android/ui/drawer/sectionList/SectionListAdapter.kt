package de.taz.app.android.ui.drawer.sectionList

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.FontHelper
import kotlinx.coroutines.*


class SectionListAdapter(
    private val fragment: SectionDrawerFragment
) : RecyclerView.Adapter<SectionListAdapter.SectionListAdapterViewHolder>() {

    private var issueOperations: IssueOperations? = null
    private var fontHelper = FontHelper.getInstance(fragment.context?.applicationContext)
    private var typeFace: Typeface? = null

    private val sectionRepository =
        SectionRepository.getInstance(fragment.context?.applicationContext)

    private var currentJob: Job? = null

    private var sectionListObserver: Observer<List<SectionStub>>? = null
    private var sectionListLiveData: LiveData<List<SectionStub>>? = null
    val sectionList
        get() = sectionListLiveData?.value ?: emptyList()

    var activePosition = RecyclerView.NO_POSITION
        set(value) {
            val oldValue = field
            field = value
            if (value >= 0 && sectionList.size > value) {
                notifyItemChanged(value)
            }
            if (oldValue >= 0 && sectionList.size > value) {
                notifyItemChanged(oldValue)
            }
        }

    fun setIssueOperations(newIssueOperations: IssueOperations?) {
        if (issueOperations?.tag != newIssueOperations?.tag) {
            sectionListObserver?.let { sectionListLiveData?.removeObserver(it) }
            sectionListObserver = null
            this.issueOperations = newIssueOperations

            fragment.lifecycleScope.launch(Dispatchers.Main) {
                typeFace = if (issueOperations?.isWeekend == true) {
                    fontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                } else {
                    fragment.defaultTypeface
                }
            }
        }
    }

    fun getIssueOperations(): IssueOperations? {
        return issueOperations
    }

    fun show() {
        if (sectionListObserver == null) {
            currentJob?.cancel()
            currentJob = fragment.lifecycleScope.launch(Dispatchers.IO) {

                issueOperations?.let { issueStub ->
                    sectionListLiveData =
                        sectionRepository.getSectionStubsLiveDataForIssueOperations(
                            issueStub
                        )
                    fragment.lifecycleScope.launchWhenResumed {
                        sectionListObserver = Observer<List<SectionStub>> {
                            notifyDataSetChanged()
                        }.also {
                            sectionListLiveData?.observeDistinct(
                                fragment.viewLifecycleOwner,
                                it
                            )
                        }
                    }
                }

                fragment.activity?.runOnUiThread {
                    notifyDataSetChanged()
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
        val sectionStub = sectionList[position]
        sectionStub.let {
            holder.textView.apply {
                typeface = this@SectionListAdapter.typeFace
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
        holder.textView.typeface = typeFace
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = sectionList.size

    fun positionOf(sectionFileName: String): Int? {
        return sectionList.indexOfFirst { it.sectionFileName == sectionFileName }
    }

}
