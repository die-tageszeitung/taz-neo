package de.taz.app.android.ui.settings.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.fragment_error_report.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ErrorReportFragment : BaseMainFragment<ErrorReportContract.Presenter>(), ErrorReportContract.View {

    override val presenter = ErrorReportPresenter()
    private val fileHelper = FileHelper.getInstance()
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
        log.debug("OS version: ${System.getProperty("os.version")}")
        log.debug("android build version: ${android.os.Build.VERSION()}")
        log.debug("android device: ${android.os.Build.DEVICE}")
        log.debug("android build model: ${android.os.Build.MODEL}")
        log.debug("android build product: ${android.os.Build.PRODUCT}")
        log.debug("android build display: ${android.os.Build.DISPLAY}")
        log.debug("android build manufacturer: ${android.os.Build.MANUFACTURER}")


        view.apply {
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

    override fun sendErrorReport(email: String?, message: String?, lastAction: String?, conditions: String?) {
        log.debug("OS version: ${System.getProperty("os.version")}")
        log.debug("android build version: ${android.os.Build.VERSION()}")
        log.debug("android device: ${android.os.Build.DEVICE}")
        log.debug("android build model: ${android.os.Build.MODEL}")
        log.debug("android build product: ${android.os.Build.PRODUCT}")

        context?.let { context ->
            val storageType = fileHelper.getFilesDir(context)
            val errorProtocol = Log.trace.toString()

            CoroutineScope(Dispatchers.IO).launch {
                apiService.sendErrorReport(email, message, lastAction, conditions, storageType, errorProtocol)
                log.debug("Sending an error report")
            }
            ToastHelper.getInstance().makeToast("sending bug report")
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }
}
