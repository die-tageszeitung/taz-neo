package de.taz.app.android.ui.bottomSheet.datePicker

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import de.taz.app.android.base.BasePresenter

class DatePickerPresenter  : BasePresenter<DatePickerContract.View, DatePickerDataController>(
    DatePickerDataController::class.java
), DatePickerContract.Presenter {

}