package de.taz.app.android.ui.search

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import de.taz.app.android.R
import de.taz.app.android.singletons.DateHelper
import java.util.*

class DatePickerFragment(private val buttonView: View) : DialogFragment() {

    private val viewModel by activityViewModels<SearchResultPagerViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = Calendar.getInstance()
        val year = c[Calendar.YEAR]
        val month = c[Calendar.MONTH]
        val day = c[Calendar.DAY_OF_MONTH]
        val dialog = DatePickerDialog(requireActivity(), ::onDateSet, year, month, day)
        dialog.datePicker.maxDate = c.timeInMillis
        return dialog
    }

    private fun onDateSet(view: DatePicker?, year: Int, month: Int, day: Int) {
        val dateString = "$year-${month + 1}-$day"
        val date = DateHelper.stringToDate(dateString)
        val localizedDateString =
            date?.let { DateHelper.dateToMediumLocalizedString(it) }.toString()

        (buttonView as Button).text = localizedDateString

        if (buttonView.id == R.id.timeslot_pub_date_from) {
            viewModel.pubDateFrom.postValue(dateString)
        }
        if (buttonView.id == R.id.timeslot_pub_date_until) {
            viewModel.pubDateUntil.postValue(dateString)
        }
        dismiss()
    }
}