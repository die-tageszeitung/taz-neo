package de.taz.app.android.ui.settings.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.login.LoginFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ErrorReportFragment : BaseMainFragment<ErrorReportContract.Presenter>(), ErrorReportContract.View {

    override val presenter = ErrorReportPresenter()
    private val log by Log
    val apiService = ApiService.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_error_report, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)

        view.apply {
            findViewById<Button>(R.id.fragment_error_report_send_button)
                .setOnClickListener {
                    val email = findViewById<EditText>(R.id.fragment_error_report_email).text.toString()
                    val message = findViewById<EditText>(R.id.fragment_error_report_message).text.toString()
                    val lastAction = findViewById<EditText>(R.id.fragment_error_report_last_action).text.toString()
                    val conditions = findViewById<EditText>(R.id.fragment_error_report_conditions).text.toString()
                    sendErrorReport(email, message, lastAction, conditions)
                }
        }
    }

    override fun sendErrorReport(email: String?, message: String?, lastAction: String?, conditions: String?) {
        //this@ErrorReportFragment.getMainView()?.showMainFragment(LoginFragment())
        log.debug("OS version: ${System.getProperty("os.version")}")
        CoroutineScope(Dispatchers.IO).launch {
            apiService.sendErrorReport(email, message, lastAction, conditions)
            log.debug("Sending an error report")
        }
        ToastHelper.getInstance().makeToast("sending bug report")
    }
}
