package de.taz.app.android.ui.drawer.sectionList

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.monkey.observeDistinctUntil
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.*
import kotlinx.android.synthetic.main.fragment_drawer_sections.*
import kotlinx.coroutines.*

const val ACTIVE_POSITION = "active position"

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment(R.layout.fragment_drawer_sections) {
    private val issueContentViewModel: IssueContentViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueContentViewModel::class.java)
    }
    private lateinit var sectionListAdapter: SectionListAdapter

    private lateinit var fontHelper: FontHelper
    private lateinit var issueRepository: IssueRepository
    private lateinit var momentRepository: MomentRepository

    private var defaultTypeface: Typeface? = null
    private var weekendTypeface: Typeface? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fontHelper = FontHelper.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        momentRepository = MomentRepository.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionListAdapter = SectionListAdapter(::onSectionItemClickListener, requireActivity().theme)
        defaultTypeface = ResourcesCompat.getFont(requireContext(), R.font.aktiv_grotesk_bold)
        weekendTypeface = runBlocking { fontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME) }
        sectionListAdapter.typeface = defaultTypeface

        issueContentViewModel.sectionListLiveData.observe(this) { sectionList ->
            sectionListAdapter.sectionList = sectionList
        }

        issueContentViewModel.issueStubAndDisplayableKeyLiveData.observe(this) { (issueStub, _ ) ->
            if (issueStub.isWeekend) {
                sectionListAdapter.typeface = weekendTypeface
            } else {
                sectionListAdapter.typeface = defaultTypeface
            }
        }

        restore(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_drawer_sections_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
            adapter = sectionListAdapter
        }

        fragment_drawer_sections_moment.setOnClickListener {
            getMainView()?.showHome(skipToIssue = issueContentViewModel.issueStubAndDisplayableKeyLiveData.value?.first)
            getMainView()?.closeDrawer()
        }

        fragment_drawer_sections_imprint.apply {
            setOnClickListener {
                showImprint()
            }
        }

        issueContentViewModel.issueStubAndDisplayableKeyLiveData.observe(this.viewLifecycleOwner) { (issueStub, _) ->
            showIssueStub(issueStub)
            lifecycleScope.launch(Dispatchers.Main) {
                fragment_drawer_sections_imprint.apply {
                    typeface = if (issueStub.isWeekend) {
                        fontHelper?.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                    } else {
                        defaultTypeface
                    }
                    visibility = View.VISIBLE
                }
            }
        }

        issueContentViewModel.activeSectionTitleLiveData.observe(this.viewLifecycleOwner) {
            if (it != null) {
                setActiveSection(it)
            }
        }

        issueContentViewModel.imprintArticleLiveData.observe(this.viewLifecycleOwner) {
            if (it != null) {
                issueContentViewModel.displayableKeyLiveData.value?.let { displayableKey ->
                    setActiveSection(displayableKey)
                }
            }
        }

    }

    private fun onSectionItemClickListener(clickedSection: SectionStub) {
        issueContentViewModel.setDisplayable(clickedSection.key)
        getMainView()?.closeDrawer()
    }

    private fun restore(savedInstanceState: Bundle?) {
        savedInstanceState?.apply {
            sectionListAdapter.activePosition = getInt(ACTIVE_POSITION, RecyclerView.NO_POSITION)
        }
    }

    private fun showIssueStub(issueStub: IssueStub) = activity?.runOnUiThread {
        lifecycleScope.launchWhenResumed {
            setMomentDate(issueStub)
            showMoment(issueStub)
        }
        view?.scrollY = 0
        view?.animate()?.alpha(1f)?.duration = 500
    }

    private fun setActiveSection(activePosition: Int) = activity?.runOnUiThread {
        sectionListAdapter.activePosition = activePosition
        if (activePosition != RecyclerView.NO_POSITION) {
            fragment_drawer_sections_imprint?.apply {
                setTextColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.drawer_sections_item,
                        null
                    )
                )
            }
        }
    }

    private fun setActiveSection(sectionFileName: String) {
        if (sectionFileName == issueContentViewModel.imprintArticleLiveData.value?.key) {
            setActiveSection(RecyclerView.NO_POSITION)
            setImprintActive()
        } else {
            setImprintInactive()
            sectionListAdapter?.positionOf(sectionFileName)?.let {
                setActiveSection(it)
            }
        }

    }

    private fun setImprintActive() {
        fragment_drawer_sections_imprint.apply {
            setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.drawer_sections_item_highlighted,
                    null
                )
            )
        }
    }

    private fun setImprintInactive() {
        fragment_drawer_sections_imprint.apply {
            setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.drawer_sections_item,
                    null
                )
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        sectionListAdapter.activePosition.let {
            outState.putInt(ACTIVE_POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

    private suspend fun showMoment(issueStub: IssueStub) = withContext(Dispatchers.IO) {
        val moment = momentRepository?.get(issueStub)
        moment?.apply {
            if (!isDownloaded(context?.applicationContext)) {
                download(context?.applicationContext)
            }
            lifecycleScope.launchWhenResumed {
                isDownloadedLiveData(context?.applicationContext).observeDistinctUntil(
                    viewLifecycleOwner,
                    {
                        if (it) {
                            fragment_drawer_sections_moment?.apply {
                                displayIssue(issueStub)
                                visibility = View.VISIBLE
                            }
                        }
                    }, { it }
                )
            }
        }

    }

    private fun showImprint() {
        issueContentViewModel.imprintArticleLiveData.value?.key?.let {
            issueContentViewModel.setDisplayable(it)
            getMainView()?.closeDrawer()
        }
    }


    private fun setMomentDate(issueStub: IssueStub) {
        fragment_drawer_sections_date?.text =
            DateHelper.stringToLongLocalizedString(issueStub.date)
    }

    override fun onDestroyView() {
        fragment_drawer_sections_list.adapter = null
        super.onDestroyView()
    }

}
