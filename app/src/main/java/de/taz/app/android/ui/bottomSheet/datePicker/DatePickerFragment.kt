package de.taz.app.android.ui.bottomSheet.datePicker

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.moment.MomentView
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_date_picker.*
import kotlinx.android.synthetic.main.fragment_cover_flow_item.*
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.android.synthetic.main.view_archive_item.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatePickerFragment :
    BaseFragment<DatePickerPresenter>(R.layout.fragment_bottom_sheet_date_picker),
    DatePickerContract.View {

    private val log by Log
    private val issueRepository = IssueRepository.getInstance()
    private var feedList: List<Feed> = emptyList()
    private val feedMap
        get() = feedList.associateBy { it.name }
    private val dateHelper = DateHelper.getInstance()

    override val presenter = DatePickerPresenter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        fragment_bottom_sheet_date_picker.maxDate = dateHelper.today()

        fragment_bottom_sheet_date_picker_confirm_button?.setOnClickListener {
            val day = fragment_bottom_sheet_date_picker.dayOfMonth
            val year = fragment_bottom_sheet_date_picker.year
            val monthShort = fragment_bottom_sheet_date_picker.month + 1
            val month= if (monthShort > 10) monthShort.toString() else "0${monthShort}"

            (this.parentFragment as DialogFragment).dismiss() //close bottomSheet
            ToastHelper.getInstance().showToast("new date set: $day.$month.$year")
            getLifecycleOwner().lifecycleScope.launch {
                setIssue(feedName = "taz", date="$year-$month-$day")
            }
        }
    }

    private suspend fun setIssue(feedName: String, date: String){
        log.debug("call setIssue()")
        log.debug("with feedName: $feedName, date:$date")
        //getItem(position)?.let { issueStub ->
        //    this.getLifecycleOwner().lifecycleScope.launch {
        //        val momentView = viewHolder.itemView.findViewById<MomentView>(R.id.fragment_cover_flow_item)
        //        momentView.presenter.setIssue(issueStub, feedMap[issueStub.feedName], dateFormat= DateFormat.LongWithoutWeekDay)
        //    }
        //}
        withContext(Dispatchers.IO) {
            log.debug("before stub loading")
            val latestIssue = issueRepository.getLatestIssueStub()
            log.debug("latest stub: $latestIssue")
            val issueStub = issueRepository.getIssueStubByFeedAndDate(feedName, date, status = IssueStatus.regular)
            log.debug("selected issueStub is: $issueStub")
            issueStub?.let {
                log.debug("activity is: $activity")
                val momentView = activity?.findViewById<MomentView>(R.id.fragment_cover_flow_item)
                //val momentView = fragmentManager?.findFragmentById(R.id.fragment_cover_flow_item)?.view?.findViewById<MomentView>(R.id.fragment_cover_flow_item)
                //this@DatePickerFragment.view?.findViewById<MomentView>(R.id.fragment_cover_flow_item)
                log.debug("momentView is: $momentView")
                //momentView.presenter.setIssue(issueStub, feedMap[issueStub.feedName], dateFormat= DateFormat.LongWithoutWeekDay)
            }
            /*
              TODO
               - use proper status (regular if logged in; public if not)
               - issue has to be downloaded if not on the device
               - load next possible issue if issue for selected date does not exist (i.e. user selected a Sunday)
               - bound datepicker to begin of taz-today
             */
            /*
            val issue = issueRepository.getIssue(issueStub)

            getMainView()?.apply {
                // start download if not yet downloaded
                if (!issue.isDownloaded()) {
                    getApplicationContext().let { applicationContext ->
                        DownloadService.download(applicationContext, issue)
                    }
                }

                // set main issue
                getMainDataController().setIssueOperations(issueStub)

                issue.sectionList.first().let { firstSection ->
                    showInWebView(firstSection)
                }
            }*/
        }
    }
}
