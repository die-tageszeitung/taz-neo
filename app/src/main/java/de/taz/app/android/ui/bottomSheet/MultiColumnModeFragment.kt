package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentMultiColumnModeBottomSheetBinding
import de.taz.app.android.monkey.doNotFlattenCorners
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import kotlinx.coroutines.launch

class MultiColumnModeBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentMultiColumnModeBottomSheetBinding>() {

    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var generalDataStore: GeneralDataStore

    companion object {
        const val TAG = "MultiColumnModeBottomSheet"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            val width = resources.displayMetrics.widthPixels
            viewBinding.description.text = resources.getString(
                R.string.fragment_bottom_sheet_multi_column_mode_description,
                width
            )
            generalDataStore.multiColumnModeBottomSheetAlreadyShown.set(true)
        }

        viewBinding.positiveButton.setOnClickListener {
            lifecycleScope.launch {
                tazApiCssDataStore.multiColumnMode.set(true)
                dismiss()
            }
        }

        viewBinding.negativeButton.setOnClickListener { dismiss() }
        viewBinding.buttonClose.setOnClickListener { dismiss() }

        (dialog as BottomSheetDialog).behavior.doNotFlattenCorners()
    }
}