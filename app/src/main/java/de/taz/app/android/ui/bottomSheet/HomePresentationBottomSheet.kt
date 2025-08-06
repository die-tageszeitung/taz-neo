package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentBottomSheetHomePresentationBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.ui.home.HomeFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


class HomePresentationBottomSheet :
    ViewBindingBottomSheetFragment<FragmentBottomSheetHomePresentationBinding>() {

    private lateinit var generalDataStore: GeneralDataStore

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //adapt UI to homeFragmentState
        generalDataStore.homeFragmentState.asFlow()
            .flowWithLifecycle(lifecycle)
            .onEach {
                setSelectedHomeState(it)
            }.launchIn(lifecycleScope)

        // adapt UI to pdfMode
        generalDataStore.pdfMode.asFlow()
            .flowWithLifecycle(lifecycle)
            .onEach {
                setPdfMode(it)
            }
            .launchIn(lifecycleScope)

    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewBinding.apply {
            buttonClose.setOnClickListener {
                dismiss()
            }

            pdfViewMode.setOnClickListener {
                lifecycleScope.launch { generalDataStore.pdfMode.set(true) }
            }
            mobileViewMode.setOnClickListener {
                lifecycleScope.launch { generalDataStore.pdfMode.set(false) }
            }

            coverflowMode.setOnClickListener {
                lifecycleScope.launch { generalDataStore.homeFragmentState.set(HomeFragment.State.COVERFLOW) }
            }
            archiveMode.setOnClickListener {
                lifecycleScope.launch { generalDataStore.homeFragmentState.set(HomeFragment.State.ARCHIVE) }
            }

        }

    }

    private fun setSelectedHomeState(state: HomeFragment.State) {
        val selected: View
        val selectedIcon: View
        val selectedText: View
        val deselected: View
        val deselectedIcon: View
        val deselectedText: View

        when (state) {
            HomeFragment.State.COVERFLOW -> {
                selected = viewBinding.coverflowMode
                selectedIcon = viewBinding.coverflowModeIcon
                selectedText = viewBinding.coverflowModeText
                deselected = viewBinding.archiveMode
                deselectedIcon = viewBinding.archiveModeIcon
                deselectedText = viewBinding.archiveModeText
            }

            HomeFragment.State.ARCHIVE -> {
                selected = viewBinding.archiveMode
                selectedIcon = viewBinding.archiveModeIcon
                selectedText = viewBinding.archiveModeText
                deselected = viewBinding.coverflowMode
                deselectedIcon = viewBinding.coverflowModeIcon
                deselectedText = viewBinding.coverflowModeText
            }
        }

        selected.background = ResourcesCompat.getDrawable(
            resources,
            R.drawable.text_settings_rounded_bg_selected,
            null
        )
        selectedIcon.setColorFilter(
            ResourcesCompat.getColor(
                resources,
                R.color.bottom_sheet_background_selected_text_color,
                null
            )
        )
        selectedText.setTextColor(
            ResourcesCompat.getColor(
                resources,
                R.color.bottom_sheet_background_selected_text_color,
                null
            )
        )

        deselected.background = null
        deselectedIcon.setColorFilter(
            ResourcesCompat.getColor(
                resources,
                R.color.textColor,
                null
            )
        )
        deselectedText.setTextColor(
            ResourcesCompat.getColor(
                resources,
                R.color.textColor,
                null
            )
        )
    }

    private fun setPdfMode(isPdfMode: Boolean) {
        val selected: View
        val selectedIcon: View
        val selectedText: View
        val deselected: View
        val deselectedIcon: View
        val deselectedText: View

        if (isPdfMode) {
            selected = viewBinding.pdfViewMode
            selectedIcon = viewBinding.pdfViewModeIcon
            selectedText = viewBinding.pdfViewModeText
            deselected = viewBinding.mobileViewMode
            deselectedIcon = viewBinding.mobileViewModeIcon
            deselectedText = viewBinding.mobileViewModeText
        } else {
            selected = viewBinding.mobileViewMode
            selectedIcon = viewBinding.mobileViewModeIcon
            selectedText = viewBinding.mobileViewModeText
            deselected = viewBinding.pdfViewMode
            deselectedIcon = viewBinding.pdfViewModeIcon
            deselectedText = viewBinding.pdfViewModeText
        }

        selected.background = ResourcesCompat.getDrawable(
            resources,
            R.drawable.text_settings_rounded_bg_selected,
            null
        )
        selectedIcon.setColorFilter(
            ResourcesCompat.getColor(
                resources,
                R.color.bottom_sheet_background_selected_text_color,
                null
            )
        )
        selectedText.setTextColor(
            ResourcesCompat.getColor(
                resources,
                R.color.bottom_sheet_background_selected_text_color,
                null
            )
        )

        deselected.background = null
        deselectedIcon.setColorFilter(
            ResourcesCompat.getColor(
                resources,
                R.color.textColor,
                null
            )
        )
        deselectedText.setTextColor(
            ResourcesCompat.getColor(
                resources,
                R.color.textColor,
                null
            )
        )
    }
}
