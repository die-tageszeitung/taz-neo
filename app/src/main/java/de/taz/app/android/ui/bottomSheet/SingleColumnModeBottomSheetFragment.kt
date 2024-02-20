package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentSingleColumnModeBottomSheetBinding
import de.taz.app.android.monkey.doNotFlattenCorners
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.launch

class SingleColumnModeBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentSingleColumnModeBottomSheetBinding>() {

    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var storageService: StorageService

    companion object {
        const val TAG = "SingleColumnModeBottomSheet"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.negativeButton.setOnClickListener {
            lifecycleScope.launch {
                generalDataStore.singleColumnModeBottomSheetDoNotShowAgain.set(true)
                dismiss()
            }
        }

        viewBinding.positiveButton.setOnClickListener { dismiss() }
        viewBinding.buttonClose.setOnClickListener { dismiss() }

        (dialog as BottomSheetDialog).behavior.doNotFlattenCorners()
    }
}