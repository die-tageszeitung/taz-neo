package de.taz.app.android.ui.bottomSheet.datePicker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_date_picker.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class DatePickerFragment : BottomSheetDialogFragment() {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var dataService: DataService
    private lateinit var contentService: ContentService
    private val issueFeedViewModel: IssueFeedViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        dataService = DataService.getInstance(context.applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_bottom_sheet_date_picker, container, false)
    }

    override fun onStart() {
        super.onStart()
        //this forces the sheet to appear at max height even on landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)

        //minDate and maxDate constraints. UX is somewhat whack..
        fragment_bottom_sheet_date_picker.maxDate = DateHelper.today(AppTimeZone.Default)
        log.debug("maxDate is ${DateHelper.longToString(DateHelper.today(AppTimeZone.Default))}")
        issueFeedViewModel.feed.observe(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val minDate = it.issueMinDate

                if (minDate.isNotBlank()) {
                    log.debug("minDate is $minDate")
                    DateHelper.stringToLong(minDate)?.let {
                        withContext(Dispatchers.Main) {
                            fragment_bottom_sheet_date_picker.minDate = it
                        }
                    }
                }
            }
        }

        issueFeedViewModel.currentDate.value?.let {
            // Set newly selected date to focus in DatePicker
            val calendar = Calendar.getInstance()
            calendar.time = it

            fragment_bottom_sheet_date_picker.updateDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
        }

        fragment_bottom_sheet_date_picker_confirm_button?.setOnClickListener { confirmButton ->
            val dayShort = fragment_bottom_sheet_date_picker.dayOfMonth
            val year = fragment_bottom_sheet_date_picker.year
            val monthShort = fragment_bottom_sheet_date_picker.month + 1
            val month = if (monthShort >= 10) monthShort.toString() else "0${monthShort}"
            val day = if (dayShort >= 10) dayShort.toString() else "0${dayShort}"

            loading_screen?.visibility = View.VISIBLE
            confirmButton.isClickable = false
            log.debug("new date set: $day.$month.$year")

            preventDismissal()

            lifecycleScope.launch {
                setIssue("$year-$month-$day")
            }
        }
    }

    private suspend fun setIssue(date: String) {
        log.debug("call setIssue() with date $date")
        issueFeedViewModel.feed.value?.let { feed ->
            withContext(Dispatchers.IO) {
                if (feed.publicationDates.contains(simpleDateFormat.parse(date)!!)) {
                    showIssue(IssuePublication(feed.name, date))
                } else {
                    toastHelper.showToast(R.string.issue_not_found)
                }
                // close date picker
                dismiss()
            }
        }
    }

    private suspend fun showIssue(issuePublication: IssuePublication) =
        withContext(Dispatchers.Main) {
            (parentFragment as CoverflowFragment).skipToDate(
                simpleDateFormat.parse(issuePublication.date)!!
            )
        }
}
