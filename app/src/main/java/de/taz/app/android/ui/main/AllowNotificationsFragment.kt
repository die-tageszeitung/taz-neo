package de.taz.app.android.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentAllowNotificationsBinding
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Date

interface AllowNotificationsFragmentCallback {
    fun onAllowNotificationsDone()
}

class AllowNotificationsFragment : ViewBindingFragment<FragmentAllowNotificationsBinding>() {
    private val viewModel by viewModels<SettingsViewModel>()
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var tracker: Tracker

    companion object {
        private const val ARG_PAGE = "page"
        private const val ARG_PAGE_TOTAL = "total"
        private const val ARG_HEADLINE_STRING = "headline"
        private const val ARG_FIRST_TIME = "firstTime"

        /**
         * If [firstTime] is false, we show another text per request of the UX/UI team
         */
        fun newInstance(firstTime: Boolean): AllowNotificationsFragment = AllowNotificationsFragment().apply {
            arguments = bundleOf(
                ARG_FIRST_TIME to firstTime
            )
        }

        fun newInstance(
            @StringRes headlineStringRes: Int,
            page: Int,
            pageTotal: Int,
        ): AllowNotificationsFragment = AllowNotificationsFragment().apply {
            arguments = bundleOf(
                ARG_PAGE to page,
                ARG_PAGE_TOTAL to pageTotal,
                ARG_HEADLINE_STRING to headlineStringRes,
                ARG_FIRST_TIME to true
            )
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val currentDate = simpleDateFormat.format(Date())
            generalDataStore.allowNotificationsLastTimeShown.set(currentDate)
        }

        viewBinding.apply {
            enableNotificationsButton.setOnClickListener {
                lifecycleScope.launch {
                    tryToSetNotificationsEnabled()
                    done()
                }
            }
            askLaterAction.setOnClickListener {
                done()
            }
            retainNotificationsButton.setOnClickListener {
                lifecycleScope.launch {
                    generalDataStore.allowNotificationsDoNotShowAgain.set(true)
                    done()
                }
            }

            buttonClose.setOnClickListener { done() }

            pagerTitle.apply {
                val page = arguments?.getInt(ARG_PAGE) ?: 0
                val pageTotal = arguments?.getInt(ARG_PAGE_TOTAL) ?: 0
                val headlineStringRes = arguments?.getInt(ARG_HEADLINE_STRING) ?: 0
                when {
                    page > 0 && pageTotal > 0 && headlineStringRes != 0 -> {
                        isVisible = true
                        text = getString(headlineStringRes, page, pageTotal)
                    }
                    headlineStringRes != 0 -> {
                        isVisible = true
                        text = getString(headlineStringRes)
                    }
                    else -> {
                        isVisible = false
                    }
                }
            }

            if (arguments?.getBoolean(ARG_FIRST_TIME) == true) {
                title.setText(R.string.fragment_bottom_sheet_notifications_title_01)
                description.setText(R.string.fragment_bottom_sheet_notifications_description_01)
            } else {
                title.setText(R.string.fragment_bottom_sheet_notifications_title_02)
                description.setText(R.string.fragment_bottom_sheet_notifications_description_02)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        tracker.trackAllowNotificationsDialog()
    }

    private suspend fun tryToSetNotificationsEnabled() {
        val result = viewModel.setNotificationsEnabled(true)
        val systemAllows =
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        if (!result || !systemAllows) {
            goToSettings()
        }
    }

    private fun goToSettings() {
        Intent(requireActivity(), SettingsActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun done() {
        (parentFragment as? AllowNotificationsFragmentCallback)?.onAllowNotificationsDone()
    }
}