package de.taz.app.android.ui.bottomSheet.datePicker

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetDatePickerBinding
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.util.Log
import java.util.*


class DatePickerFragment : ViewBindingBottomSheetFragment<FragmentBottomSheetDatePickerBinding>() {

    private val log by Log

    private lateinit var toastHelper: ToastHelper

    private lateinit var currentDate: Date
    private lateinit var feed: Feed

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val issueFeedViewModel: IssueFeedViewModel by activityViewModels()
        feed = requireNotNull(issueFeedViewModel.feed.value) {
            "Feed always must be set when using DatePickerFragment"
        }
        currentDate = requireNotNull(issueFeedViewModel.currentDate.value) {
            "CurrentDate must always be set when using DatePickerFragment"
        }
        toastHelper = ToastHelper.getInstance(context.applicationContext)
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

        //minDate and maxDate constraints
        viewBinding.fragmentBottomSheetDatePicker.maxDate = DateHelper.today(AppTimeZone.Default)
        log.debug("maxDate is ${DateHelper.longToString(DateHelper.today(AppTimeZone.Default))}")

        val minDate = feed.issueMinDate
        if (minDate.isNotBlank()) {
            log.debug("minDate is $minDate")
            DateHelper.stringToLong(minDate)?.let { long ->
                viewBinding.fragmentBottomSheetDatePicker.minDate = long
            }
        }

        // Set newly selected date to focus in DatePicker
        val calendar = Calendar.getInstance()
        calendar.time = currentDate

        viewBinding.fragmentBottomSheetDatePicker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        viewBinding.fragmentBottomSheetDatePickerConfirmButton.setOnClickListener { confirmButton ->
            val dayShort = viewBinding.fragmentBottomSheetDatePicker.dayOfMonth
            val year = viewBinding.fragmentBottomSheetDatePicker.year
            val monthShort = viewBinding.fragmentBottomSheetDatePicker.month + 1
            val month = if (monthShort >= 10) monthShort.toString() else "0${monthShort}"
            val day = if (dayShort >= 10) dayShort.toString() else "0${dayShort}"

            view.findViewById<View>(R.id.loading_screen)?.visibility = View.VISIBLE
            confirmButton.isClickable = false
            log.debug("new date set: $day.$month.$year")

            preventDismissal()

            setIssue("$year-$month-$day")
        }
    }

    private fun setIssue(date: String) {
        log.debug("call setIssue() with date $date")
        if (feed.publicationDates.contains(simpleDateFormat.parse(date)!!)) {
            showIssue(IssuePublication(feed.name, date))
        } else {
            toastHelper.showToast(R.string.issue_not_found)
        }
        // close date picker
        dismiss()
    }

    private fun showIssue(issuePublication: IssuePublication) =
        (parentFragment as CoverflowFragment).skipToDate(
            simpleDateFormat.parse(issuePublication.date)!!
        )
}
