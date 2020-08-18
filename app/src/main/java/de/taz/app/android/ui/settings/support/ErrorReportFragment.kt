package de.taz.app.android.ui.settings.support

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.singletons.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceStringLiveData
import kotlinx.android.synthetic.main.fragment_error_report.*
import kotlinx.android.synthetic.main.fragment_header_default.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*


class ErrorReportFragment : BaseMainFragment(R.layout.fragment_error_report) {

    private val log by Log

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coordinator.moveContentBeneathStatusBar()

        view.apply {
            fragment_header_default_title.text =
                getString(R.string.settings_header).toLowerCase(Locale.GERMAN)

            // read email from settings
            fragment_error_report_email.setText(
                requireActivity().applicationContext.getSharedPreferences(
                    PREFERENCES_AUTH,
                    Context.MODE_PRIVATE
                ).getString(PREFERENCES_AUTH_EMAIL, "")
            )

            fragment_error_report_send_button.setOnClickListener {
                loading_screen.visibility = View.VISIBLE
                val email = fragment_error_report_email.text.toString().trim()
                val message = fragment_error_report_message.text.toString().trim()
                val lastAction = fragment_error_report_last_action.text.toString().trim()
                val conditions = fragment_error_report_conditions.text.toString().trim()

                if (email.isNotEmpty() || message.isNotEmpty() || lastAction.isNotEmpty() || conditions.isNotEmpty()) {
                    sendErrorReport(email, message, lastAction, conditions)
                } else {
                    loading_screen.visibility = View.GONE
                }
            }
        }

    }

    private fun sendErrorReport(
        email: String?,
        message: String?,
        lastAction: String?,
        conditions: String?
    ) {
        context?.let { context ->
            val storageType =
                FileHelper.getInstance(activity?.applicationContext).getFilesDir(context)
            val errorProtocol = Log.trace.toString()

            val activityManager =
                this.requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalRam = "%.2f GB".format(memoryInfo.totalMem / 1073741824f)
            val usedRam =
                "%.2f GB".format((memoryInfo.totalMem - memoryInfo.availMem) / 1073741824f)

            CoroutineScope(Dispatchers.IO).launch {
                ApiService.getInstance(activity?.applicationContext).sendErrorReportAsync(
                    email,
                    message,
                    lastAction,
                    conditions,
                    storageType,
                    errorProtocol,
                    usedRam,
                    totalRam
                )
                log.debug("Sending an error report")
            }
            ToastHelper.getInstance(activity?.applicationContext)
                .showToast(R.string.toast_error_report_sent)
            parentFragmentManager.popBackStack()
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            log.debug("Show home clicked")
            showHome(skipToNewestIssue = true)
        }
    }
}
