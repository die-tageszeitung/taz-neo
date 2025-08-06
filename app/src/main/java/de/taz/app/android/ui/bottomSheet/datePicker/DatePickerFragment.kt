package de.taz.app.android.ui.bottomSheet.datePicker

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetDatePickerBinding
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.sentry.SentryWrapperLevel
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.getIndexOfDate
import de.taz.app.android.util.getSuccessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date


class DatePickerFragment : ViewBindingBottomSheetFragment<FragmentBottomSheetDatePickerBinding>() {

    companion object {
        const val TAG = "DatePickerBottomSheetFragment"
        private const val ARG_DATE = "ARG_DATE"

        fun newInstance(simpleDateString: String) = DatePickerFragment().apply {
            arguments = bundleOf(
                ARG_DATE to simpleDateString
            )
        }

        fun newInstance(date: Date) = newInstance(simpleDateFormat.format(date))
    }

    private val log by Log

    private val issueFeedViewModel: IssueFeedViewModel by activityViewModels()

    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueFeedViewModel.feed
            .flowWithLifecycle(lifecycle)
            .onEach { feed ->
                setMinAndMaxDate(feed)
            }.launchIn(lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
        //this forces the sheet to appear at max height even on landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
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

        val dateString = requireNotNull(
            arguments?.getString(ARG_DATE),
            { "ARG_DATE has to be provided please use newInstance to create DatePickerFragment" }
        )
        val date = requireNotNull(
            simpleDateFormat.parse(dateString),
            { "ARG_DATE has to be in yyyy-MM-dd format" }
        )
        calendar.time = date

        viewBinding.fragmentBottomSheetDatePicker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

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
            SentryWrapper.captureMessage(hint, SentryWrapperLevel.ERROR)
            return null
        }

        return viewBinding.fragmentBottomSheetDatePicker.findViewById(dayPickerId)
    }

    private fun setMinAndMaxDate(feed: Feed) {
        val minDate = feed.issueMinDate
        val minDateLong = DateHelper.stringToLong(minDate)
        if (minDateLong != null) {
            log.debug("minDate is $minDate")
            viewBinding.fragmentBottomSheetDatePicker.minDate = minDateLong
        }

        val maxDate = feed.issueMaxDate
        val maxDateLong = DateHelper.stringToLong(maxDate)
        if (maxDateLong != null) {
            log.debug("maxDate is $maxDate")
            viewBinding.fragmentBottomSheetDatePicker.maxDate = maxDateLong
        }
    }

    private fun setIssue(dateString: String) = lifecycleScope.launch {
        log.debug("call setIssue() with date $dateString")
        val date = requireNotNull(simpleDateFormat.parse(dateString))
        val feed = issueFeedViewModel.feed.first()
        val publicationDateIndex = feed.publicationDates.getIndexOfDate(date)
        if (publicationDateIndex != -1) {
            showIssue(IssuePublication(feed.name, dateString))
        } else {
            skipToSuccessorIssue(date)
        }
        // close date picker
        dismiss()
    }

    private fun showIssue(issuePublication: IssuePublication) {
        simpleDateFormat.parse(issuePublication.date)
            ?.let { date -> issueFeedViewModel.updateCurrentDate(date) }
    }

    private suspend fun skipToSuccessorIssue(date: Date) {
        val feed = issueFeedViewModel.feed.first()
        val successorDate = feed.publicationDates.getSuccessor(date)

        if (successorDate == null) {
            log.error("An index out of bound date selected via datepicker. Date: $date")
            toastHelper.showToast(R.string.issue_not_found)
            return
        }
        showIssue(IssuePublication(feed.name, simpleDateFormat.format(successorDate)))
    }
}
