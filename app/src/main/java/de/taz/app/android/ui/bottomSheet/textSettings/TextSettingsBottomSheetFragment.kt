package de.taz.app.android.ui.bottomSheet.textSettings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentBottomSheetTextSizeBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.SingleColumnModeBottomSheetFragment
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.ui.webview.TapIconsViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val MIN_WIDTH_TO_SHOW_DESCRIPTION_TEXTS_DP = 600

class TextSettingsBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentBottomSheetTextSizeBinding>() {

    companion object {
        const val TAG = "textSettingsBottomSheetFragment"
        private const val ARGUMENT_HIDE_MULTI_COLUMN_MODE_SWITCH = "hideMultiColumnModeSwitch"

        fun newInstance(hideMultiColumnModeSwitch: Boolean = false) =
            TextSettingsBottomSheetFragment().apply {
                arguments = bundleOf(
                    ARGUMENT_HIDE_MULTI_COLUMN_MODE_SWITCH to hideMultiColumnModeSwitch
                )
            }
    }

    private lateinit var authHelper: AuthHelper
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var tracker: Tracker
    private val viewModel by viewModels<TextSettingsViewModel>()
    private val tapIconsViewModel: TapIconsViewModel by activityViewModels()

    private val hideMultiColumnModeSwitch: Boolean
        get() = arguments?.getBoolean(ARGUMENT_HIDE_MULTI_COLUMN_MODE_SWITCH) ?: false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.goToSettings.setOnClickListener {
            onSettingsSelected()
        }

        viewBinding.fragmentBottomSheetTextSizeDecrease.setOnClickListener {
            decreaseFontSize()
        }

        viewBinding.fragmentBottomSheetTextSizePercentageWrapper.setOnClickListener {
            resetFontSize()
        }

        viewBinding.fragmentBottomSheetTextSizeIncrease.setOnClickListener {
            increaseFontSize()
        }

        viewBinding.settingDayMode.setOnClickListener {
            setNightMode(active = false)
        }

        viewBinding.settingNightMode.setOnClickListener {
            setNightMode(active = true)
        }

        viewBinding.buttonClose.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            if (resources.getBoolean(R.bool.isTablet) && authHelper.isValid() && !hideMultiColumnModeSwitch) {
                viewBinding.settingSingleColumn.setOnClickListener {
                    setMultiColumnMode(active = false)
                    maybeShowSingleColumnBottomSheet()
                    tapIconsViewModel.hideTapIcons()
                }
                viewBinding.settingMultiColumns.setOnClickListener {
                    setMultiColumnMode(active = true)
                }
            } else {
                viewBinding.fragmentBottomSheetMultiColumnMode.isVisible = false
                viewBinding.fragmentBottomSheetColumnDescription.isVisible = false
            }
        }

        viewModel.nightModeFlow
            .flowWithLifecycle(lifecycle)
            .onEach { activated ->
                if (activated) {
                    setNightModeSelected()
                } else {
                    setDayModeSelected()
                }
                setNightMode(activated)
            }.launchIn(lifecycleScope)

        viewModel.multiColumnModeFlow
            .flowWithLifecycle(lifecycle)
            .onEach { activated ->
                if (activated) {
                    setMultiColumnModeSelected()
                } else {
                    setSingleColumnModeSelected()
                }
            }.launchIn(lifecycleScope)

        viewModel.textSizeFlow
            .flowWithLifecycle(lifecycle)
            .onEach { textSizePercentage ->
                setFontSizePercentage(textSizePercentage)
            }.launchIn(lifecycleScope)

        val displayWidth =
            resources.displayMetrics.widthPixels.toFloat() / resources.displayMetrics.density
        if (displayWidth < MIN_WIDTH_TO_SHOW_DESCRIPTION_TEXTS_DP) {
            hideDescriptionTextViews()
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackTextSettingsDialog()
    }

    private fun setNightMode(active: Boolean) {
        viewModel.setNightMode(active)
    }

    private fun setMultiColumnMode(active: Boolean) {
        viewModel.setMultiColumnMode(active)
        if (active) {
            tracker.trackArticleColumnModeEnableEvent()
        } else {
            tracker.trackArticleColumnModeDisableEvent()
        }
    }

    private fun setFontSizePercentage(percent: String) {
        viewBinding.fragmentBottomSheetTextSizePercentage.text = "$percent%"
    }

    private fun onSettingsSelected() {
        Intent(requireActivity(), SettingsActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun decreaseFontSize() {
        viewModel.decreaseFontSize()
    }

    private fun resetFontSize() {
        viewModel.resetFontSize()
    }

    private fun increaseFontSize() {
        viewModel.increaseFontSize()
    }

    private fun setDayModeSelected() {
        viewBinding.apply {
            settingDayMode.background = ResourcesCompat.getDrawable(
                resources,
                R.drawable.text_settings_rounded_bg_selected,
                null
            )
            settingDayModeIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )
            settingDayModeText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )

            settingNightMode.background = null
            settingNightModeIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
            settingNightModeText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
        }
    }

    private fun setNightModeSelected() {
        viewBinding.apply {
            settingNightMode.background = ResourcesCompat.getDrawable(
                resources,
                R.drawable.text_settings_rounded_bg_selected,
                null
            )
            settingNightModeIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )
            settingNightModeText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )

            settingDayMode.background = null
            settingDayModeIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
            settingDayModeText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
        }
    }

    private fun setSingleColumnModeSelected() {
        viewBinding.apply {
            settingSingleColumn.background = ResourcesCompat.getDrawable(
                resources,
                R.drawable.text_settings_rounded_bg_selected,
                null
            )
            settingSingleColumnIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )
            settingSingleColumnText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )

            settingMultiColumns.background = null
            settingMultiColumnIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
            settingMultiColumnText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
        }
    }

    private fun setMultiColumnModeSelected() {
        viewBinding.apply {
            settingMultiColumns.background = ResourcesCompat.getDrawable(
                resources,
                R.drawable.text_settings_rounded_bg_selected,
                null
            )
            settingMultiColumnIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )
            settingMultiColumnText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.bottom_sheet_background_selected_text_color,
                    null
                )
            )

            settingSingleColumn.background = null
            settingSingleColumnIcon.setColorFilter(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
            settingSingleColumnText.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.textColor,
                    null
                )
            )
        }
    }

    private fun hideDescriptionTextViews() {
        viewBinding.apply {
            fragmentBottomSheetTextSizeDescription.isVisible = false
            fragmentBottomSheetNightModeDescription.isVisible = false
            fragmentBottomSheetColumnDescription.isVisible = false
            fragmentBottomSheetTextSizeSettingsDescription.isVisible = false
        }
    }

    private fun maybeShowSingleColumnBottomSheet() {
        lifecycleScope.launch {
            val doNotShowAgain = generalDataStore.singleColumnModeBottomSheetDoNotShowAgain.get()
            if (!doNotShowAgain)
                if (childFragmentManager.findFragmentByTag(
                        SingleColumnModeBottomSheetFragment.TAG) == null) {
                    SingleColumnModeBottomSheetFragment().show(
                        childFragmentManager,
                        SingleColumnModeBottomSheetFragment.TAG
                    )
                }
        }
    }
}