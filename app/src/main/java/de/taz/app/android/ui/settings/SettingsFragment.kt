package de.taz.app.android.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.*
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.ExperimentalSearchActivity
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.getStorageLocationCaption
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : BaseViewModelFragment<SettingsViewModel>(R.layout.fragment_settings) {
    private val log by Log

    private var storedIssueNumber: String? = null
    private var lastStorageLocation: StorageLocation? = null

    private lateinit var toastHelper: ToastHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.apply {
            findViewById<TextView>(R.id.fragment_header_default_title).text =
                getString(R.string.settings_header).lowercase(Locale.GERMAN)
            findViewById<TextView>(R.id.fragment_settings_category_general).text =
                getString(R.string.settings_category_general).lowercase(Locale.GERMAN)
            findViewById<TextView>(R.id.fragment_settings_category_text).text =
                getString(R.string.settings_category_text).lowercase(Locale.GERMAN)
            findViewById<TextView>(R.id.fragment_settings_category_account).text =
                getString(R.string.settings_category_account).lowercase(Locale.GERMAN)
            findViewById<TextView>(R.id.fragment_settings_category_support).text =
                getString(R.string.settings_category_support).lowercase(Locale.GERMAN)

            findViewById<TextView>(R.id.fragment_settings_general_keep_issues).setOnClickListener {
                showKeepIssuesDialog()
            }

            findViewById<Button>(R.id.fragment_settings_support_report_bug).setOnClickListener {
                reportBug()
            }

            findViewById<TextView>(R.id.fragment_settings_faq)?.setOnClickListener {
                openFAQ()
            }

            findViewById<TextView>(R.id.fragment_settings_account_manage_account)
                .setOnClickListener {
                    activity?.startActivityForResult(
                        Intent(activity, LoginActivity::class.java),
                        ACTIVITY_LOGIN_REQUEST_CODE
                    )
                }

            findViewById<TextView>(R.id.fragment_settings_welcome_slides)
                .setOnClickListener {
                    activity?.startActivity(
                        Intent(activity, WelcomeActivity::class.java)
                    )
                }

            findViewById<TextView>(R.id.fragment_settings_terms)
                .setOnClickListener {
                    val intent = Intent(activity, WebViewActivity::class.java)
                    intent.putExtra(WEBVIEW_HTML_FILE, WEBVIEW_HTML_FILE_TERMS)
                    activity?.startActivity(intent)
                }

            findViewById<TextView>(R.id.fragment_settings_revocation)
                .setOnClickListener {
                    val intent = Intent(activity, WebViewActivity::class.java)
                    intent.putExtra(WEBVIEW_HTML_FILE, WEBVIEW_HTML_FILE_REVOCATION)
                    activity?.startActivity(intent)
                }

            findViewById<View>(R.id.settings_text_decrease_wrapper).setOnClickListener {
                decreaseFontSize()
            }
            findViewById<View>(R.id.settings_text_increase_wrapper).setOnClickListener {
                increaseFontSize()
            }
            findViewById<View>(R.id.settings_text_size_wrapper).setOnClickListener {
                resetFontSize()
            }

            findViewById<View>(R.id.fragment_settings_storage_location).setOnClickListener {
                StorageSelectionDialog(requireContext(), viewModel).show()
            }

            fragment_settings_night_mode?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    enableNightMode()
                } else {
                    disableNightMode()
                }
            }

            fragment_settings_account_logout.setOnClickListener {
                logout()
            }

            fragment_settings_version_number?.text =
                getString(R.string.settings_version_number, BuildConfig.VERSION_NAME)

            fragment_settings_auto_download_wifi_switch?.setOnCheckedChangeListener { _, isChecked ->
                setDownloadOnlyInWifi(isChecked)
            }

            fragment_settings_auto_download_switch?.setOnCheckedChangeListener { _, isChecked ->
                setDownloadEnabled(isChecked)
            }

            if (BuildConfig.DEBUG) {
                inflateExperimentalOptions()
            }
        }


        viewModel.apply {
            fontSizeLiveData.observeDistinct(viewLifecycleOwner) { textSize ->
                textSize.toIntOrNull()?.let { textSizeInt ->
                    showFontSize(textSizeInt)
                }
            }
            nightModeLiveData.observeDistinct(viewLifecycleOwner) { nightMode ->
                showNightMode(nightMode)
            }
            storedIssueNumberLiveData.observeDistinct(viewLifecycleOwner) { storedIssueNumber ->
                showStoredIssueNumber(storedIssueNumber)
            }
            downloadOnlyWifiLiveData.observeDistinct(viewLifecycleOwner) { onlyWifi ->
                showOnlyWifi(onlyWifi)
            }
            downloadAutomaticallyLiveData.observeDistinct(viewLifecycleOwner) { downloadsEnabled ->
                showDownloadsEnabled(downloadsEnabled)
            }

            storageLocationLiveData.observeDistinct(viewLifecycleOwner) { storageLocation ->
                if (lastStorageLocation != null && lastStorageLocation != storageLocation) {
                    toastHelper.showToast(R.string.settings_storage_migration_hint)
                }
                lastStorageLocation = storageLocation
                val storageLocationString = requireContext().getStorageLocationCaption(
                    storageLocation
                )
                view.findViewById<TextView>(R.id.settings_storage_location_value).text =
                    storageLocationString
            }
        }

        val authHelper = AuthHelper.getInstance(activity?.applicationContext)
        authHelper.status.asLiveData().observeDistinct(viewLifecycleOwner) { authStatus ->
            if (authStatus in arrayOf(
                    AuthStatus.valid,
                    AuthStatus.elapsed
                )
            ) {
                showLogoutButton()
            } else {
                showManageAccountButton()
            }
        }
        authHelper.email.asLiveData().observeDistinct(viewLifecycleOwner) { email ->
            fragment_settings_account_email.text = email
        }
    }

    private fun showKeepIssuesDialog() {
        context?.let {
            val dialog = MaterialAlertDialogBuilder(it)
                .setView(R.layout.dialog_settings_keep_number)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    (dialog as AlertDialog).findViewById<EditText>(
                        R.id.dialog_settings_keep_number
                    )?.text.toString().toIntOrNull()?.let { number ->
                        setStoredIssueNumber(number.coerceAtLeast(1))
                        dialog.hide()
                    }
                }
                .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()
            dialog.show()
            storedIssueNumber?.let {
                dialog.findViewById<TextView>(
                    R.id.dialog_settings_keep_number
                )?.text = storedIssueNumber
            }
        }
    }

    private fun inflateExperimentalOptions() {
        val experimentalContainer = view?.findViewById<FrameLayout>(R.id.experimental_container)
        val experimentalOptionsView = layoutInflater.inflate(R.layout.view_experimental_options, experimentalContainer)
        experimentalOptionsView.findViewById<TextView>(R.id.expirimental_search_button)
            .setOnClickListener {
                startActivity(
                    Intent(
                        requireActivity(),
                        ExperimentalSearchActivity::class.java
                    )
                )
            }
    }

    private fun showStoredIssueNumber(number: String) {
        storedIssueNumber = number
        val text = HtmlCompat.fromHtml(
            getString(R.string.settings_general_keep_number_issues, number),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        view?.findViewById<TextView>(R.id.fragment_settings_general_keep_issues)?.text = text
    }

    private fun showNightMode(nightMode: Boolean) {
        view?.findViewById<SwitchCompat>(R.id.fragment_settings_night_mode)?.isChecked = nightMode
    }

    private fun showOnlyWifi(onlyWifi: Boolean) {
        view?.findViewById<SwitchCompat>(R.id.fragment_settings_auto_download_wifi_switch)?.isChecked =
            onlyWifi
    }

    private fun showDownloadsEnabled(downloadsEnabled: Boolean) {
        view?.findViewById<SwitchCompat>(R.id.fragment_settings_auto_download_switch)?.isChecked =
            downloadsEnabled
        view?.findViewById<SwitchCompat>(R.id.fragment_settings_auto_download_wifi_switch)?.apply {
            isEnabled = downloadsEnabled
        }
    }

    private fun showFontSize(textSize: Int) {
        view?.findViewById<TextView>(
            R.id.settings_text_size
        )?.text = getString(R.string.percentage, textSize)
    }

    private fun showLogoutButton() {
        fragment_settings_account_email.visibility = View.VISIBLE
        fragment_settings_account_logout.visibility = View.VISIBLE
        fragment_settings_account_manage_account.visibility = View.GONE
    }

    private fun showManageAccountButton() {
        fragment_settings_account_email.visibility = View.GONE
        fragment_settings_account_logout.visibility = View.GONE
        fragment_settings_account_manage_account.visibility = View.VISIBLE
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            Intent(requireActivity(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(this)
            }
        }
    }

    private fun setStoredIssueNumber(number: Int) {
        log.debug("setKeepNumber: $number")
        viewModel.storedIssueNumberLiveData.postValue(number.toString())
    }

    private fun disableNightMode() {
        log.debug("disableNightMode")
        viewModel.setNightMode(false)
    }

    private fun enableNightMode() {
        log.debug("enableNightMode")
        viewModel.setNightMode(true)
    }


    private fun decreaseFontSize() {
        viewModel.decreaseFontSize()
    }

    private fun increaseFontSize() {
        log.debug("increaseFontSize")
        viewModel.increaseFontSize()
    }

    private fun resetFontSize() {
        CoroutineScope(Dispatchers.IO).launch {
            log.debug("resetFontSize")
            viewModel.resetFontSize()
        }
    }

    private fun reportBug() {
        Intent(requireActivity(), ErrorReportActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun setDownloadOnlyInWifi(onlyWifi: Boolean) {
        viewModel.setOnlyWifi(onlyWifi)
    }

    private fun setDownloadEnabled(downloadEnabled: Boolean) {
        viewModel.setDownloadsEnabled(downloadEnabled)
    }

    private fun logout() = requireActivity().lifecycleScope.launch(Dispatchers.IO) {
        val authHelper = AuthHelper.getInstance(activity?.applicationContext)
        authHelper.token.set("")
        authHelper.status.set(AuthStatus.notValid)
    }

    private fun openFAQ() {
        val color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(requireContext(), Uri.parse("https://blogs.taz.de/app-faq/")) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper =
                ToastHelper.getInstance(context)
            toastHelper.showToast(R.string.toast_unknown_error)
        }
    }
}