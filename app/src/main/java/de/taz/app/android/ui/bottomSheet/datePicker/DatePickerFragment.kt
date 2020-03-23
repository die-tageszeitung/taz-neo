package de.taz.app.android.ui.bottomSheet.datePicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_date_picker.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class DatePickerFragment : BottomSheetDialogFragment() {

    private val log by Log

    private val issueRepository = IssueRepository.getInstance()
    private var feedList: List<Feed> = emptyList()
    private val feedMap
        get() = feedList.associateBy { it.name }
    private val dateHelper = DateHelper.getInstance()

    private var issueStub: IssueStub? = null
    private var weakActivityReference: WeakReference<MainContract.View?>? = null
    private var feed : Feed? = null

    companion object {
        fun create(
            mainActivity: MainContract.View?
        ): DatePickerFragment {
            val fragment = DatePickerFragment()
            fragment.weakActivityReference = WeakReference(mainActivity)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)

        fragment_bottom_sheet_date_picker.maxDate = dateHelper.today()
        lifecycleScope.launch(Dispatchers.IO) {
            feed = FeedRepository.getInstance().get("taz")
            feed?.let {feed ->
                log.debug("minDate is: ${feed.issueMinDate}")
                fragment_bottom_sheet_date_picker.minDate = dateHelper.stringToLong(feed.issueMinDate)
            }
        }

        fragment_bottom_sheet_date_picker_confirm_button?.setOnClickListener {
            val day = fragment_bottom_sheet_date_picker.dayOfMonth
            val year = fragment_bottom_sheet_date_picker.year
            val monthShort = fragment_bottom_sheet_date_picker.month + 1
            val month= if (monthShort > 10) monthShort.toString() else "0${monthShort}"

            dismiss() //close bottomSheet
            ToastHelper.getInstance().showToast("new date set: $day.$month.$year")
            //lifecycleScope.launch(Dispatchers.IO) {
            lifecycleScope.launch() {
                setIssue("$year-$month-$day")
                //issueStub = issueRepository.getIssueStubByFeedAndDate("taz", "$year-$month-$day", status = IssueStatus.regular)
                //issueStub?.let { issueStub ->
                //    log.debug("will call showIssue for $issueStub")
                //    weakActivityReference?.get()?.showIssue(issueStub)
                //}
            }
        }
    }

    private suspend fun setIssue(date: String){
        log.debug("call setIssue()")
        withContext(Dispatchers.IO) {
            // use proper issue status (regular if logged in; public if not)
            val authStatus = AuthHelper.getInstance().authStatus
            var issueStatus : IssueStatus = IssueStatus.public
            if (authStatus == AuthStatus.valid
                || authStatus == AuthStatus.alreadyLinked
                || authStatus == AuthStatus.tazIdNotLinked) {
               issueStatus = IssueStatus.regular
            }
            //issueStub = issueRepository.getIssueStubByFeedAndDate("taz", date, issueStatus)
            issueStub = issueRepository.getLatestIssueStubByFeedAndDate("taz", date, issueStatus)
            log.debug("selected issueStub is: $issueStub")
            issueStub?.let { issueStub ->
                weakActivityReference?.get()?.showIssue(issueStub)
            }
            /*
              TODO
               - use proper status (regular if logged in; public if not)
               - load next possible issue if issue for selected date does not exist (i.e. user selected a Sunday)
               - bound datepicker to begin of taz-today
             */
        }
    }
}
