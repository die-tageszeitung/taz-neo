package de.taz.app.android.ui.bottomSheet.datePicker

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetDatePickerBinding
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.*
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.getIndexOfDate
import de.taz.app.android.util.getSuccessor
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.*


class DatePickerFragment : ViewBindingBottomSheetFragment<FragmentBottomSheetDatePickerBinding>() {

    private val log by Log

    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

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
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        //this forces the sheet to appear at max height even on landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        // this removes the translucent status of the status bar which causes some weird flickering
        // FIXME (johannes): refactor to get see why the flag is deprecated and move to general style
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    }

    override fun onResume() {
        super.onResume()
        tracker.trackIssueDatePickerDialog()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)

        // Set newly selected date to focus in DatePicker
        // This has to be done before setting the min/maxDate to prevent crashes on old Android Versions
        val calendar = Calendar.getInstance()
        calendar.time = currentDate

        viewBinding.fragmentBottomSheetDatePicker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

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

        if (BuildConfig.IS_LMD) {
            val dayPickerView = getDayPickerView()
            dayPickerView?.visibility = View.GONE
        }

        viewBinding.fragmentBottomSheetDatePickerConfirmButton.setOnClickListener { confirmButton ->
            val day: String
            val year = viewBinding.fragmentBottomSheetDatePicker.year
            val monthShort = viewBinding.fragmentBottomSheetDatePicker.month + 1
            val month = if (monthShort >= 10) monthShort.toString() else "0${monthShort}"
            if (BuildConfig.IS_LMD) {
                /* We don't know the actual publication day of every LMd issue.
                 * We set issue day here to the beginning of the month and within setIssue
                 * we jump to its existing successor (closest newer date) in the list
                 * of issue dates, which in this case will be the monthly issue
                 * of the corresponding month.*/
                day = "01"
            }
            else {
                val dayShort = viewBinding.fragmentBottomSheetDatePicker.dayOfMonth
                day = if (dayShort >= 10) dayShort.toString() else "0${dayShort}"
            }

            view.findViewById<View>(R.id.loading_screen)?.visibility = View.VISIBLE
            confirmButton.isClickable = false
            log.debug("new date set: $day.$month.$year")

            preventDismissal()

            setIssue("$year-$month-$day")
        }
    }

    // IS_LMD
    @SuppressLint("DiscouragedApi")
    private fun getDayPickerView(): View? {
        // Get the day view as it is defined on the currently supported android versions
        val dayPickerId = Resources.getSystem().getIdentifier("day", "id", "android")

        if (dayPickerId == 0) {
            val hint = "Could not get the day picker view with the id 'day'. Ensure this id is still used on all API Versions"
            log.error(hint)
            Sentry.captureMessage(hint, SentryLevel.ERROR)
            return null
        }

        return viewBinding.fragmentBottomSheetDatePicker.findViewById(dayPickerId)
    }

    private fun setIssue(dateString: String) {
        log.debug("call setIssue() with date $dateString")
        val date = requireNotNull(simpleDateFormat.parse(dateString))
        val publicationDateIndex = feed.publicationDates.getIndexOfDate(date)
        if (publicationDateIndex != -1) {
            showIssue(IssuePublication(feed.name, dateString))
        } else {
            skipToSuccessorIssue(date)
        }
        // close date picker
        dismiss()
    }

    private fun showIssue(issuePublication: IssuePublication) =
        (parentFragment as CoverflowFragment).skipToDate(
            simpleDateFormat.parse(issuePublication.date)!!
        )

    private fun skipToSuccessorIssue(date: Date) {
        val successorDate = feed.publicationDates.getSuccessor(date)

        if (successorDate == null) {
            log.error("An index out of bound date selected via datepicker. Date: $date")
            toastHelper.showToast(R.string.issue_not_found)
            return
        }
        showIssue(IssuePublication(feed.name, simpleDateFormat.format(successorDate)))
    }
}
