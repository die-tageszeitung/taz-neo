package de.taz.app.android.ui.search

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.singletons.DateHelper


class AdvancedTimeslotDialogFragment : DialogFragment() {

    private var dialogView: View? = null
    private val viewModel by activityViewModels<SearchResultPagerViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_advanced_search_timeslot, container)
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
        val radioGroup = view.findViewById<RadioGroup>(R.id.search_radio_group_timeslot_group)
        val customTimeslot = view.findViewById<ConstraintLayout>(R.id.custom_timeslot)
        val radioButtonAny =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_any)
        val radioButtonDay =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_last_day)
        val radioButtonWeek =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_last_week)
        val radioButtonMonth =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_last_month)
        val radioButtonYear =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_last_year)
        val radioButtonCustom =
            radioGroup.findViewById<RadioButton>(R.id.radio_button_custom)
        val fromField = view.findViewById<Button>(R.id.timeslot_pub_date_from)
        val untilField = view.findViewById<Button>(R.id.timeslot_pub_date_until)

        if (BuildConfig.IS_LMD) {
            radioButtonDay.visibility = View.GONE
            radioButtonWeek.visibility = View.GONE
        }

        when (viewModel.chosenTimeSlot.value) {
            getString(R.string.search_advanced_radio_timeslot_any) ->
                radioButtonAny.isChecked = true
            getString(R.string.search_advanced_radio_timeslot_last_day) ->
                radioButtonDay.isChecked = true
            getString(R.string.search_advanced_radio_timeslot_last_week) ->
                radioButtonWeek.isChecked = true
            getString(R.string.search_advanced_radio_timeslot_last_month) ->
                radioButtonMonth.isChecked = true
            getString(R.string.search_advanced_radio_timeslot_last_year) ->
                radioButtonYear.isChecked = true
            else -> {
                radioButtonCustom.isChecked = true
                customTimeslot.visibility = View.VISIBLE
                fromField.text = viewModel.pubDateFrom.value?.let {
                    DateHelper.stringToMediumLocalizedString(
                        it
                    )
                }
                untilField.text = viewModel.pubDateUntil.value?.let {
                    DateHelper.stringToMediumLocalizedString(
                        it
                    )
                }
            }
        }

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            val radioButton: View = group.findViewById(checkedId)
            val radioId: Int = group.indexOfChild(radioButton)
            val btn = group.getChildAt(radioId)
            val chosenTimeslot = (btn as RadioButton).text.toString()
            viewModel.chosenTimeSlot.postValue(chosenTimeslot)

            if (checkedId == R.id.radio_button_custom) {
                customTimeslot.visibility = View.VISIBLE
            } else {
                customTimeslot.visibility = View.GONE
            }
        }

        view.findViewById<Button>(R.id.timeslot_pub_date_from).setOnClickListener { fromView ->
            val newFragment: DialogFragment = DatePickerFragment(fromView)
            activity?.supportFragmentManager?.let { sfm -> newFragment.show(sfm, "datePickerFrom") }
        }

        view.findViewById<Button>(R.id.timeslot_pub_date_until).setOnClickListener { untilView ->
            val newFragment: DialogFragment = DatePickerFragment(untilView)
            activity?.supportFragmentManager?.let { sfm ->
                newFragment.show(
                    sfm,
                    "datePickerUntil"
                )
            }
        }
    }
}
