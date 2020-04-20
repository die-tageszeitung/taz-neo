package de.taz.app.android.ui.bottomSheet.datePicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_date_picker.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.*


class DatePickerFragment (val date: Date) : BottomSheetDialogFragment() {

    private val log by Log

    private val issueRepository = IssueRepository.getInstance()
    private val dateHelper = DateHelper.getInstance()
    private val apiService = ApiService.getInstance()

    private var issueStub: IssueStub? = null
    private var coverFlowFragment: WeakReference<CoverflowFragment?>? = null
    private var feed : Feed? = null

    companion object {
        fun create(
            coverFlowFragment: CoverflowFragment?,
            date : Date
        ): DatePickerFragment {
            val fragment = DatePickerFragment(date)
            fragment.coverFlowFragment = WeakReference(coverFlowFragment)
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_bottom_sheet_date_picker, container, false)
    }

    fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)


        //Comment in for minDate and maxDate constraints. UX is somewhat whack..
        fragment_bottom_sheet_date_picker.maxDate = dateHelper.today()
        log.debug("maxDate is ${dateHelper.longToString(dateHelper.today())}")
        lifecycleScope.launch(Dispatchers.IO) {
            feed = FeedRepository.getInstance().get("taz")
            feed?.let { feed ->
                log.debug("minDate is: ${feed.issueMinDate}")
                fragment_bottom_sheet_date_picker.minDate = dateHelper.stringToLong(feed.issueMinDate)
            }
        }

        fragment_bottom_sheet_date_picker_confirm_button?.setOnClickListener { confirmButton ->
            val dayShort = fragment_bottom_sheet_date_picker.dayOfMonth
            val year = fragment_bottom_sheet_date_picker.year
            val monthShort = fragment_bottom_sheet_date_picker.month + 1
            val month= if (monthShort >= 10) monthShort.toString() else "0${monthShort}"
            val day = if (dayShort >= 10) dayShort.toString() else "0${dayShort}"

            loading_screen?.visibility = View.VISIBLE
            confirmButton.visibility = View.GONE
            ToastHelper.getInstance().showToast("new date set: $day.$month.$year")
            log.debug("new date set: $day.$month.$year")

            preventDismissal()

            activity?.lifecycleScope?.launch() {
                setIssue("$year-$month-$day")
            }
        }

        // Set newly selected date to focus in DatePicker
        val calendar = Calendar.getInstance()
        calendar.time = date

        fragment_bottom_sheet_date_picker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private suspend fun setIssue(date: String){
        log.debug("call setIssue() with date $date")
        withContext(Dispatchers.IO) {

            val issueStub = issueRepository.getLatestIssueStubByDate(date)
            if (issueStub != null) {
                //show
                val issue = issueRepository.getIssue(issueStub)
                showIssue(issue)
            }
            else {
                issueRepository.getEarliestIssueStub()?.let { earliestIssueStub ->
                    val earliestDate = earliestIssueStub.date

                    val newIssue = apiService.getIssuesByDate(date, 1).first()
                    log.debug("newIssue is $newIssue")
                    issueRepository.save(newIssue)
                    showIssue(newIssue)

                    val missingIssuesCount = dateHelper.dayDelta(date, earliestDate)
                    val missingIssues = apiService.getIssuesByDate(earliestDate, missingIssuesCount.toInt())
                    missingIssues.forEach { issueRepository.save(it) }
                }
            }

            /*
            issueRepository.getEarliestIssueStub()?.let { earliestIssueStub ->
                var earliestDate = earliestIssueStub.date
                while (issueRepository.getLatestIssueStubByDate(date) == null)  {
                    //get stuff from api
                    val newIssues = apiService.getIssuesByDate(earliestDate)
                    newIssues.forEach { issueRepository.save(it) }
                    earliestDate = newIssues.last().date
                }
                val selectedIssueStub = issueRepository.getLatestIssueStubByDate(date)
                coverFlowFragment?.get()?.let { coverFlowFragment ->
                    val issueStubPosition = coverFlowFragment.coverFlowPagerAdapter.filterIssueStubs().indexOf(selectedIssueStub)
                    coverFlowFragment.skipToPosition(issueStubPosition)
                    dismiss()
                }

            }
            */
        }
    }

    private suspend fun showIssue(issue: Issue) {
        dismiss() //close datePicker
        getMainView()?.apply {
            // start download if not yet downloaded
            if (!issue.isDownloaded()) {
                DownloadService.getInstance().download(issue)
            }

            // set main issue
            setDrawerIssue(issue)

            issue.sectionList.first().let { firstSection ->
                showInWebView(firstSection.key)
            }
        }

        //TODO reset datePicker + coverFlow onto current issue

    }
}
