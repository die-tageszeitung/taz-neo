package de.taz.app.android.ui.search

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R

class AdvancedSortByDialog : DialogFragment() {

    private var dialogView: View? = null
    private val viewModel by activityViewModels<SearchResultPagerViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_advanced_search_sort_by, container)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme).apply {
            dialogView =
                onCreateView(LayoutInflater.from(requireContext()), null, savedInstanceState)

            dialogView?.let { onViewCreated(it, savedInstanceState) }
            setView(dialogView)
            setNegativeButton(R.string.cancel_button) { dialog, _ -> dialog.dismiss() }
            setPositiveButton(R.string.search_advanced_apply_filter) { dialog, _ -> dialog.dismiss() }
        }.create()
    }

    override fun getView(): View? {
        return dialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val radioGroup = view.findViewById<RadioGroup>(R.id.search_radio_group_sort_by)
        val radioButtonRelevance =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_sort_by_relevance)
        val radioButtonAppearance =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_sort_by_appearance)
        val radioButtonActuality =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_sort_by_actuality)

        when (viewModel.chosenSortBy.value) {
            getString(R.string.search_advanced_radio_sort_by_relevance) ->
                radioButtonRelevance.isChecked = true
            getString(R.string.search_advanced_radio_sort_by_appearance) ->
                radioButtonAppearance.isChecked = true
            getString(R.string.search_advanced_radio_sort_by_actuality) ->
                radioButtonActuality.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val radioButton: View = group.findViewById(checkedId)
            val radioId: Int = group.indexOfChild(radioButton)
            val btn = group.getChildAt(radioId)
            val chosenSortBy = (btn as RadioButton).text.toString()
            viewModel.chosenSortBy.postValue(chosenSortBy)
        }
    }
}