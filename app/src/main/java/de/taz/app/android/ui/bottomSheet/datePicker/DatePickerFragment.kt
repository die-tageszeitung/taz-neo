package de.taz.app.android.ui.bottomSheet.datePicker

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import de.taz.app.android.R
import de.taz.app.android.base.BaseFragment
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_date_picker.*

class DatePickerFragment :
    BaseFragment<DatePickerPresenter>(R.layout.fragment_bottom_sheet_date_picker),
    DatePickerContract.View {

    private val log by Log

    override val presenter = DatePickerPresenter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("created a new date picker")
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        fragment_bottom_sheet_date_picker_confirm_button?.setOnClickListener {
            val day = fragment_bottom_sheet_date_picker.dayOfMonth
            val year = fragment_bottom_sheet_date_picker.year
            val month= fragment_bottom_sheet_date_picker.month + 1

            (this.parentFragment as DialogFragment).dismiss()
            ToastHelper.getInstance().showToast("new date set: $day.$month.$year")
        }
    }
}
