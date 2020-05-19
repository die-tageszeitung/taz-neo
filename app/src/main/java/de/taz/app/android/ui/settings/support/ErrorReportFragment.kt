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
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.singletons.ToastHelper
import kotlinx.android.synthetic.main.fragment_error_report.*
import kotlinx.android.synthetic.main.fragment_header_default.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*


class ErrorReportFragment :
    BaseMainFragment<ErrorReportContract.Presenter>(R.layout.fragment_error_report),
    ErrorReportContract.View {

    override val presenter = ErrorReportPresenter()
    private val fileHelper = FileHelper.getInstance()
    private val log by Log
    val apiService = ApiService.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)

        coordinator.moveContentBeneathStatusBar()

        view.apply {
            fragment_header_default_title.text =
                getString(R.string.settings_header).toLowerCase(Locale.GERMAN)

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

        presenter.onViewCreated(savedInstanceState)
    }

    override fun sendErrorReport(
        email: String?,
        message: String?,
        lastAction: String?,
        conditions: String?
    ) {
        context?.let { context ->
            val storageType = fileHelper.getFilesDir(context)
            val errorProtocol = Log.trace.toString()

            val activityManager = this.requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalRam = "%.2f GB".format(memoryInfo.totalMem/ 1073741824f)
            val usedRam = "%.2f GB".format((memoryInfo.totalMem - memoryInfo.availMem)/ 1073741824f)

            CoroutineScope(Dispatchers.IO).launch {
                apiService.sendErrorReport(
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
            ToastHelper.getInstance().showToast(R.string.toast_error_report_sent)
            parentFragmentManager.popBackStack()
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }
}
