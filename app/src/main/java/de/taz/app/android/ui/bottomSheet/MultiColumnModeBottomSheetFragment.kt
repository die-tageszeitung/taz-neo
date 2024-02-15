package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.KNILE_LIGHT_RESOURCE_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentMultiColumnModeBottomSheetBinding
import de.taz.app.android.monkey.doNotFlattenCorners
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import kotlinx.coroutines.launch

class MultiColumnModeBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentMultiColumnModeBottomSheetBinding>() {


    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var fontHelper: FontHelper
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
        fontHelper = FontHelper.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)

        // this removes the translucent status of the status bar which causes some weird flickering
        // FIXME (johannes): refactor to get see why the flag is deprecated and move to general style
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyKnileTypeFaceToTitle()

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

        (dialog as BottomSheetDialog).behavior.doNotFlattenCorners()
    }

    private fun applyKnileTypeFaceToTitle() {
        lifecycleScope.launch {
            val knileFileEntry = fileEntryRepository.get(KNILE_LIGHT_RESOURCE_FILE_NAME)
            val knileFile = knileFileEntry?.let(storageService::getFile)
            knileFile?.let {
                fontHelper.getTypeFace(it)?.let { typeface ->
                    viewBinding.title.typeface = typeface
                }
            }
        }
    }
}