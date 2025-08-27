package de.taz.app.android.singletons

import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.util.Log
import de.taz.app.android.util.getIndexOfDate
import de.taz.app.android.util.getSuccessor
import java.util.Date
import java.util.TimeZone

class DatePickerHelper(
    val dateToHighlight: Date,
    val feed: Feed,
    val viewModel: IssueFeedViewModel
) {

    private val log by Log
    val timezone: TimeZone = TimeZone.getDefault()

    fun initializeDatePicker(): MaterialDatePicker<Long> {
        val localizedDateToHighlight =
            dateToHighlight.time + timezone.getOffset(dateToHighlight.time)

        // Build constraints
        val dateConstraints = getDateConstraints()

        val picker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.datepicker_title)
                .setNegativeButtonText(R.string.cancel_button)
                .setSelection(localizedDateToHighlight)
                .setCalendarConstraints(dateConstraints)
                .build()
                .apply {
                    addOnPositiveButtonClickListener {
                        val selected = this.selection
                        if (selected != null) {
                            setIssue(Date(selected))
                        }
                    }
                }

        return picker
    }

    // region date picker helper functions
    private fun setIssue(unlocalizedDate: Date) {
        val dateTime =
            unlocalizedDate.time - timezone.getOffset(unlocalizedDate.time)
        val date = Date(dateTime)

        log.debug("call setIssue() with date $date")

        val publicationDateIndex = feed.publicationDates.getIndexOfDate(date)

        if (publicationDateIndex != -1) {
            showIssue(date)
        } else {
            skipToSuccessorIssue(date)
        }
    }

    private fun showIssue(date: Date) {
        viewModel.requestDateFocus(date)
    }

    private fun skipToSuccessorIssue(date: Date) {
        val successorDate = feed.publicationDates.getSuccessor(date)

        if (successorDate == null) {
            log.error("An index out of bound date selected via date picker. Date: $date")
            return
        }
        showIssue(successorDate)
    }

    private fun getMinMaxDate(): Pair<Long, Long>? {
        val minDate = feed.issueMinDate
        val minDateLong = DateHelper.stringToLong(minDate)
        if (minDateLong == null) {
            return null
        }

        val maxDate = feed.issueMaxDate
        val maxDateLong = DateHelper.stringToLong(maxDate)
        if (maxDateLong == null) {
            return null
        }
        return minDateLong to maxDateLong
    }

    private fun getDateConstraints(): CalendarConstraints? {
        val dateConstraints = getMinMaxDate()
        return if (dateConstraints != null) {
            CalendarConstraints.Builder()
                .setStart(dateConstraints.first)
                .setEnd(dateConstraints.second).setValidator(DateValidatorPointBackward.now())
                .build()
        } else {
            null
        }
    }
    // endregion
}