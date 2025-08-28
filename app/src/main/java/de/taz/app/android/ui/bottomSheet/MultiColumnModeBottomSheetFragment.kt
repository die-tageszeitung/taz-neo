package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentMultiColumnModeBottomSheetBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import kotlinx.coroutines.launch

class MultiColumnModeBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentMultiColumnModeBottomSheetBinding>() {


    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var storageService: StorageService
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var tracker: Tracker

    companion object {
        const val TAG = "MultiColumnModeBottomSheet"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
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
                tracker.trackArticleColumnModeEnableEvent()
                dismiss()
            }
        }

        viewBinding.negativeButton.setOnClickListener { dismiss() }
        viewBinding.buttonClose.setOnClickListener { dismiss() }
    }
}