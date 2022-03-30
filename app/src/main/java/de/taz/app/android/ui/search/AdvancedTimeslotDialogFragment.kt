package de.taz.app.android.ui.search

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R


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
        }.create()
    }

    override fun getView(): View? {
        return dialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val radioGroup = view.findViewById<RadioGroup>(R.id.search_radio_group_timeslot_group)
        val customTimeslot = view.findViewById<LinearLayout>(R.id.custom_timeslot)

        radioGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.radio_button_custom) {
                customTimeslot.visibility = View.VISIBLE
            } else {
                customTimeslot.visibility = View.GONE
                val radioButton: View = group.findViewById(checkedId)
                val radioId: Int = group.indexOfChild(radioButton)
                val btn = group.getChildAt(radioId)
                val chosenTimeslot = (btn as RadioButton).text.toString()
                viewModel.chosenTimeSlot.postValue(chosenTimeslot)
            }
        }
        view.findViewById<Button>(R.id.timeslot_pub_date_from).setOnClickListener { fromView ->
            val newFragment: DialogFragment = DatePickerFragment(fromView)
            activity?.supportFragmentManager?.let { sfm -> newFragment.show(sfm, "datePickerFrom") }
        }
        view.findViewById<Button>(R.id.timeslot_pub_date_until).setOnClickListener { untilView ->
            val newFragment: DialogFragment = DatePickerFragment(untilView)
            activity?.supportFragmentManager?.let { sfm -> newFragment.show(sfm, "datePickerUntil") }
        }
        view.findViewById<Button>(R.id.dialog_cancel).setOnClickListener {
            viewModel.chosenTimeSlot.postValue(getString(R.string.search_advanced_radio_timeslot_any))
            this.dismiss()
        }
        view.findViewById<Button>(R.id.dialog_apply).setOnClickListener {
            this.dismiss()
        }
    }
}
