package de.taz.app.android.ui.search

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.Dialog
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.databinding.DialogAdvancedSearchTimeslotBinding
import de.taz.app.android.singletons.DateHelper
import java.util.Calendar


class AdvancedTimeslotDialogFragment : DialogFragment() {

    private val viewModel by activityViewModels<SearchResultViewModel>()
    private var viewBinding: DialogAdvancedSearchTimeslotBinding? = null

    private var datePickerDialog: DatePickerDialog? = null

    private var currentFilter: PublicationDateFilter = PublicationDateFilter.Any
        set(value) {
            field = value
            viewBinding?.let { setRadioButtons(it, value) }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val viewBinding = DialogAdvancedSearchTimeslotBinding.inflate(layoutInflater)

        this.viewBinding = viewBinding
        this.currentFilter = viewModel.selectedSearchOptions.value.advancedOptions.publicationDateFilter

        viewBinding.searchRadioGroupTimeslotGroup.setOnCheckedChangeListener { _, _ ->
            val filter = when {
                viewBinding.radioButtonAny.isChecked -> PublicationDateFilter.Any
                viewBinding.radioButtonLastMonth.isChecked -> PublicationDateFilter.Last31Days
                viewBinding.radioButtonLastYear.isChecked -> PublicationDateFilter.Last365Days
                viewBinding.radioButtonLastWeek.isChecked -> PublicationDateFilter.Last7Days
                viewBinding.radioButtonLastDay.isChecked -> PublicationDateFilter.LastDay
                viewBinding.radioButtonCustom.isChecked -> PublicationDateFilter.Custom(null, null)
                else -> error("One radio button must be checked")
            }

            currentFilter = filter
        }

        viewBinding.timeslotPubDateFrom.setOnClickListener {
            showDatePickerDialog(getOrCreateCustomFilter(), isUntilDate = false)
        }

        viewBinding.timeslotPubDateUntil.setOnClickListener {
            showDatePickerDialog(getOrCreateCustomFilter(), isUntilDate = true)
        }

        if (BuildConfig.IS_LMD) {
            viewBinding.apply {
                radioButtonLastDay.isVisible = false
                radioButtonLastWeek.isVisible = false
            }
        }

        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(viewBinding.root)
            .setNegativeButton(R.string.cancel_button) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ -> onApplyFilter() }
            .create()
    }

    override fun onDestroy() {
        super.onDestroy()
        datePickerDialog?.dismiss()
        datePickerDialog = null
        viewBinding = null
    }

    private fun onApplyFilter() {
        viewModel.setSelectedAdvancedOptions(
            viewModel.selectedSearchOptions.value.advancedOptions.copy(
                publicationDateFilter = currentFilter
            )
        )
    }

    private fun setRadioButtons(
        viewBinding: DialogAdvancedSearchTimeslotBinding,
        publicationDateFilter: PublicationDateFilter
    ) {
        val checkedButton = when (publicationDateFilter) {
            PublicationDateFilter.Any -> viewBinding.radioButtonAny
            PublicationDateFilter.Last31Days -> viewBinding.radioButtonLastMonth
            PublicationDateFilter.Last365Days -> viewBinding.radioButtonLastYear
            PublicationDateFilter.Last7Days -> viewBinding.radioButtonLastWeek
            PublicationDateFilter.LastDay -> viewBinding.radioButtonLastDay
            is PublicationDateFilter.Custom -> viewBinding.radioButtonCustom
        }
        checkedButton.isChecked = true

        if (publicationDateFilter is PublicationDateFilter.Custom) {
            viewBinding.apply {
                customTimeslot.isVisible = true
                timeslotPubDateFrom.text =
                    publicationDateFilter.from
                        ?.let { DateHelper.dateToMediumLocalizedString(it) }
                        ?: ""
                timeslotPubDateUntil.text =
                    publicationDateFilter.until
                        ?.let { DateHelper.dateToMediumLocalizedString(it) }
                        ?: ""
            }
        } else {
            viewBinding.customTimeslot.isVisible = false
        }
    }

    private fun getOrCreateCustomFilter(): PublicationDateFilter.Custom {
        return when (val filter = currentFilter) {
            is PublicationDateFilter.Custom -> filter
            else -> PublicationDateFilter.Custom(null, null)
        }
    }

    private fun showDatePickerDialog(
        currentFilter: PublicationDateFilter.Custom,
        isUntilDate: Boolean,
    ) {
        val today = Calendar.getInstance()

        val minDate = if (isUntilDate && currentFilter.from != null) {
            currentFilter.from
        } else {
            viewModel.minPublicationDate
        }

        val maxDate = if (!isUntilDate && currentFilter.until != null) {
            currentFilter.until
        } else {
            today.time
        }

        val onDateSetListener = OnDateSetListener { _, year, month, day ->
            val dateSet = Calendar.getInstance().apply {
                set(year, month, day)
            }.time

            val dateSetValid = dateSet.coerceIn(minDate, maxDate)
            if (isUntilDate) {
                this.currentFilter = currentFilter.copy(until = dateSetValid)
            } else {
                this.currentFilter = currentFilter.copy(from = dateSetValid)
            }
        }

        datePickerDialog?.dismiss()
        datePickerDialog =
            DatePickerDialog(
                requireContext(),
                onDateSetListener,
                today[Calendar.YEAR],
                today[Calendar.MONTH],
                today[Calendar.DAY_OF_MONTH]
            ).apply {
                datePicker.minDate = minDate.time
                datePicker.maxDate = maxDate.time
                show()
            }
    }

}
