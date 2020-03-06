package de.taz.app.android.ui.settings.support

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
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

        view.apply {
            fragment_header_default_title.text =
                getString(R.string.settings_header).toLowerCase(Locale.GERMAN)

            fragment_error_report_send_button.setOnClickListener {
                val email = fragment_error_report_email.text.toString()
                val message = fragment_error_report_message.text.toString()
                val lastAction = fragment_error_report_last_action.text.toString()
                val conditions = fragment_error_report_conditions.text.toString()
                sendErrorReport(email, message, lastAction, conditions)
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

            CoroutineScope(Dispatchers.IO).launch {
                apiService.sendErrorReport(
                    email,
                    message,
                    lastAction,
                    conditions,
                    storageType,
                    errorProtocol
                )
                log.debug("Sending an error report")
            }
            ToastHelper.getInstance().showToast("sending bug report")
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }
}
