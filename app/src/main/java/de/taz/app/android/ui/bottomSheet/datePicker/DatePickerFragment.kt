package de.taz.app.android.ui.bottomSheet.datePicker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.Feed
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_date_picker.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.*


class DatePickerFragment(val date: Date, val feed: Feed) : BottomSheetDialogFragment() {

    private val log by Log

    private var coverFlowFragment: WeakReference<CoverflowFragment?>? = null

    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var dataService: DataService

    companion object {
        fun create(
            coverFlowFragment: CoverflowFragment?,
            feed: Feed,
            date: Date
        ): DatePickerFragment {
            val fragment = DatePickerFragment(date, feed)
            fragment.coverFlowFragment = WeakReference(coverFlowFragment)
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)

        //minDate and maxDate constraints. UX is somewhat whack..
        fragment_bottom_sheet_date_picker.maxDate = DateHelper.today(AppTimeZone.Default)
        log.debug("maxDate is ${DateHelper.longToString(DateHelper.today(AppTimeZone.Default))}")
        lifecycleScope.launch(Dispatchers.IO) {
            val minDate = feed.issueMinDate

            if (!minDate.isBlank()) {
                log.debug("minDate is $minDate")
                DateHelper.stringToLong(minDate)?.let {
                    withContext(Dispatchers.Main) {
                        fragment_bottom_sheet_date_picker.minDate = it
                    }
                }
            }
        }

        fragment_bottom_sheet_date_picker_confirm_button?.setOnClickListener { confirmButton ->
            val dayShort = fragment_bottom_sheet_date_picker.dayOfMonth
            val year = fragment_bottom_sheet_date_picker.year
            val monthShort = fragment_bottom_sheet_date_picker.month + 1
            val month = if (monthShort >= 10) monthShort.toString() else "0${monthShort}"
            val day = if (dayShort >= 10) dayShort.toString() else "0${dayShort}"

            loading_screen?.visibility = View.VISIBLE
            confirmButton.visibility = View.GONE
            log.debug("new date set: $day.$month.$year")

            preventDismissal()

            lifecycleScope.launch {
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

    private suspend fun setIssue(date: String) {
        log.debug("call setIssue() with date $date")
        withContext(Dispatchers.IO) {
            val issueStub = try {
                dataService.getIssueStub(
                    IssuePublication(feed.name, date)

                )
            } catch (e: ConnectivityException.Recoverable) {
                toastHelper.showNoConnectionToast()
                null
            }

            if (issueStub != null) {
                showIssue(issueStub.issueKey)
            } else {
                toastHelper.showToast(R.string.issue_not_found)
            }
            // close date picker
            dismiss()
        }
    }

    private suspend fun showIssue(issueKey: IssueKey) = withContext(Dispatchers.Main) {
        coverFlowFragment?.get()?.skipToKey(issueKey)
    }
}
