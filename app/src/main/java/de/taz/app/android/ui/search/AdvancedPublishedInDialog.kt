package de.taz.app.android.ui.search

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.api.variables.SearchFilter
import de.taz.app.android.databinding.DialogAdvancedSearchPublishedInBinding

class AdvancedPublishedInDialog : DialogFragment() {

    private val viewModel by activityViewModels<SearchResultViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val viewBinding = DialogAdvancedSearchPublishedInBinding.inflate(layoutInflater)

        val filter = viewModel.selectedSearchOptions.value.advancedOptions.searchFilter
        setRadioButtons(viewBinding, filter)

        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(viewBinding.root)
            .setNegativeButton(R.string.cancel_button) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ ->
                onApplyFilter(viewBinding)
            }
            .create()
    }

    private fun setRadioButtons(
        viewBinding: DialogAdvancedSearchPublishedInBinding,
        filter: SearchFilter
    ) {
        val checkedButton = when (filter) {
            SearchFilter.all ->
                viewBinding.radioButtonPublishedInAny

            SearchFilter.taz ->
                viewBinding.radioButtonPublishedInTaz

            SearchFilter.LMd ->
                viewBinding.radioButtonPublishedInLmd

            SearchFilter.Kontext ->
                viewBinding.radioButtonPublishedInKontext

            SearchFilter.weekend ->
                viewBinding.radioButtonPublishedInWeekend
        }
        checkedButton.isChecked = true
    }

    private fun onApplyFilter(viewBinding: DialogAdvancedSearchPublishedInBinding) {
        val filter = when {
            viewBinding.radioButtonPublishedInAny.isChecked -> SearchFilter.all
            viewBinding.radioButtonPublishedInTaz.isChecked -> SearchFilter.taz
            viewBinding.radioButtonPublishedInLmd.isChecked -> SearchFilter.LMd
            viewBinding.radioButtonPublishedInKontext.isChecked -> SearchFilter.Kontext
            viewBinding.radioButtonPublishedInWeekend.isChecked -> SearchFilter.weekend
            else -> error("One radio button must be checked")
        }

        viewModel.setSelectedAdvancedOptions(
            viewModel.selectedSearchOptions.value.advancedOptions.copy(
                searchFilter = filter
            )
        )
    }
}