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

class AdvancedPublishedInDialog : DialogFragment() {

    private var dialogView: View? = null
    private val viewModel by activityViewModels<SearchResultPagerViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_advanced_search_published_in, container)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext(), theme).apply {
            dialogView =
                onCreateView(LayoutInflater.from(requireContext()), null, savedInstanceState)

            dialogView?.let { onViewCreated(it, savedInstanceState) }
            setView(dialogView)
        }.create()
    }

    override fun getView(): View? {
        return dialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val radioGroup = view.findViewById<RadioGroup>(R.id.search_radio_group_published_in)
        val radioButtonAny =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_published_in_any)
        val radioButtonTaz =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_published_in_taz)
        val radioButtonLmd =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_published_in_lmd)
        val radioButtonKontext =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_published_in_kontext)
        val radioButtonWeekend =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_published_in_weekend)

        when (viewModel.chosenPublishedIn.value) {
            getString(R.string.search_advanced_radio_published_in_any) ->
                radioButtonAny.isChecked = true
            getString(R.string.search_advanced_radio_published_in_taz) ->
                radioButtonTaz.isChecked = true
            getString(R.string.search_advanced_radio_published_in_lmd) ->
                radioButtonLmd.isChecked = true
            getString(R.string.search_advanced_radio_published_in_kontext) ->
                radioButtonKontext.isChecked = true
            getString(R.string.search_advanced_radio_published_in_weekend) ->
                radioButtonWeekend.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val radioButton: View = group.findViewById(checkedId)
            val radioId: Int = group.indexOfChild(radioButton)
            val btn = group.getChildAt(radioId)
            val chosenPublishedIn = (btn as RadioButton).text.toString()
            viewModel.chosenPublishedIn.postValue(chosenPublishedIn)

            dismiss()
        }
    }
}