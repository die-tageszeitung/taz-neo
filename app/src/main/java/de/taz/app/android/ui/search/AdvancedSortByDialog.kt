package de.taz.app.android.ui.search

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.api.models.Sorting
import de.taz.app.android.databinding.DialogAdvancedSearchSortByBinding

class AdvancedSortByDialog : DialogFragment() {

    private val viewModel by activityViewModels<SearchResultViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val viewBinding = DialogAdvancedSearchSortByBinding.inflate(layoutInflater)

        val sorting = viewModel.selectedSearchOptions.value.advancedOptions.sorting
        setRadioButtons(viewBinding, sorting)

        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setView(viewBinding.root)
            .setNegativeButton(R.string.cancel_button) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(R.string.search_advanced_apply_filter) { _, _ ->
                onApplyFilter(viewBinding)
            }
            .create()
    }

    private fun setRadioButtons(viewBinding: DialogAdvancedSearchSortByBinding, sorting: Sorting) {
        val checkedButton = when (sorting) {
            Sorting.relevance -> viewBinding.radioButtonSortByRelevance
            Sorting.actuality -> viewBinding.radioButtonSortByActuality
            Sorting.appearance -> viewBinding.radioButtonSortByAppearance
        }
        checkedButton.isChecked = true
    }

    private fun onApplyFilter(viewBinding: DialogAdvancedSearchSortByBinding) {
        val sorting = when {
            viewBinding.radioButtonSortByRelevance.isChecked -> Sorting.relevance
            viewBinding.radioButtonSortByAppearance.isChecked -> Sorting.appearance
            viewBinding.radioButtonSortByActuality.isChecked -> Sorting.actuality
            else -> error("One radio button must be checked")
        }
        viewModel.setSelectedAdvancedOptions(
            viewModel.selectedSearchOptions.value.advancedOptions.copy(
                sorting = sorting
            )
        )
    }
}