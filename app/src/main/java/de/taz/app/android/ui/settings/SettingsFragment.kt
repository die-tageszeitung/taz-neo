package de.taz.app.android.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import kotlinx.android.synthetic.main.fragment_settings.*
import java.util.*

class SettingsFragment : BaseMainFragment<SettingsContract.Presenter>(R.layout.fragment_settings),
    SettingsContract.View {

    override val presenter = SettingsPresenter()

    private var storedIssueNumber: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)

        view.apply {
            findViewById<TextView>(R.id.fragment_header_default_title).text =
                getString(R.string.settings_header).toLowerCase(
                    Locale.GERMAN
                )

            findViewById<TextView>(R.id.fragment_settings_general_keep_issues).apply {
                setOnClickListener {
                    showKeepIssuesDialog()
                }
            }

            findViewById<TextView>(R.id.fragment_settings_support_report_bug).apply {
                setOnClickListener {
                    presenter.reportBug()
                }
            }

            findViewById<Button>(R.id.fragment_settings_account_manage_account)
                .setOnClickListener {
                    activity?.startActivityForResult(
                        Intent(activity, LoginActivity::class.java),
                        ACTIVITY_LOGIN_REQUEST_CODE
                    )
                }

            findViewById<View>(R.id.settings_text_decrease).setOnClickListener {
                presenter.decreaseTextSize()
            }
            findViewById<View>(R.id.settings_text_increase).setOnClickListener {
                presenter.increaseTextSize()
            }
            findViewById<View>(R.id.settings_text_size).setOnClickListener {
                presenter.resetTextSize()
            }

            findViewById<Switch>(R.id.fragment_settings_night_mode).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        presenter.enableNightMode()
                    } else {
                        presenter.disableNightMode()
                    }
                }
            }

            fragment_settings_account_logout.setOnClickListener {
                presenter.logout()
            }

            findViewById<TextView>(R.id.fragment_settings_version_number)?.text =
                BuildConfig.VERSION_NAME

            findViewById<Switch>(R.id.fragment_settings_auto_download_wifi_switch).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    presenter.setDownloadOnlyInWifi(isChecked)
                }
            }
        }

        presenter.onViewCreated(savedInstanceState)
    }

    private fun showKeepIssuesDialog() {
        context?.let {
            val dialog = AlertDialog.Builder(ContextThemeWrapper(it, R.style.DialogTheme))
                .setView(R.layout.dialog_settings_keep_number)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    val editText = (dialog as AlertDialog).findViewById<EditText>(
                        R.id.dialog_settings_keep_number
                    )
                    editText.text.toString().toIntOrNull()?.let { number ->
                        presenter.setStoredIssueNumber(number)
                        dialog.hide()
                    }
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()

            dialog.show()
            storedIssueNumber?.let {
                dialog?.findViewById<TextView>(
                    R.id.dialog_settings_keep_number
                )?.text = storedIssueNumber
            }
        }
    }

    override fun showStoredIssueNumber(number: String) {
        storedIssueNumber = number
        val text = getString(R.string.settings_general_keep_number_issues, number)
        view?.findViewById<TextView>(R.id.fragment_settings_general_keep_issues)?.text = text
    }

    override fun showNightMode(nightMode: Boolean) {
        view?.findViewById<Switch>(R.id.fragment_settings_night_mode)?.isChecked = nightMode
    }

    override fun showOnlyWifi(onlyWifi: Boolean) {
        view?.findViewById<Switch>(R.id.fragment_settings_auto_download_wifi_switch)?.isChecked =
            onlyWifi
    }

    override fun showTextSize(textSize: Int) {
        view?.findViewById<TextView>(
            R.id.settings_text_size
        )?.text = getString(R.string.percentage, textSize)
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

    override fun showLogoutButton() {
        fragment_settings_account_email.visibility = View.VISIBLE
        fragment_settings_account_logout.visibility = View.VISIBLE
        fragment_settings_account_manage_account.visibility = View.GONE
    }

    override fun showManageAccountButton() {
        fragment_settings_account_email.visibility = View.GONE
        fragment_settings_account_logout.visibility = View.GONE
        fragment_settings_account_manage_account.visibility = View.VISIBLE
    }

}
