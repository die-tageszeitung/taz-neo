package de.taz.app.android.ui.settings

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import de.taz.app.android.*
import de.taz.app.android.BuildConfig.FLAVOR_graphql
import de.taz.app.android.BuildConfig.FLAVOR_source
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.CancellationInfo
import de.taz.app.android.api.models.CancellationStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.FragmentSettingsBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.getStorageLocationCaption
import io.sentry.Sentry
import kotlinx.coroutines.*
import java.util.*
import java.util.regex.Pattern

@Suppress("UNUSED")
class SettingsFragment : BaseViewModelFragment<SettingsViewModel, FragmentSettingsBinding>() {
    private val log by Log

    private var storedIssueNumber: Int? = null
    private var lastStorageLocation: StorageLocation? = null

    private lateinit var apiService: ApiService
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository
    private lateinit var storageService: StorageService
    private lateinit var toastHelper: ToastHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(requireContext().applicationContext)
        contentService = ContentService.getInstance(requireContext().applicationContext)
        issueRepository = IssueRepository.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.fragment_header_default_title)?.apply {
            text = context.getString(
                R.string.settings
            ).lowercase(Locale.getDefault())
        }
        viewBinding.apply {
            fragmentSettingsSupportReportBug.setOnClickListener { reportBug() }
            fragmentSettingsFaq.setOnClickListener { openFAQ() }
            fragmentSettingsAccountManageAccount.setOnClickListener {
                activity?.startActivityForResult(
                    Intent(activity, LoginActivity::class.java),
                    ACTIVITY_LOGIN_REQUEST_CODE
                )
            }
            fragmentSettingsWelcomeSlides.setOnClickListener {
                activity?.startActivity(
                    Intent(activity, WelcomeActivity::class.java)
                )
            }

            fragmentSettingsTerms.setOnClickListener {
                activity?.apply {
                    val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_TERMS)
                    startActivity(intent)
                }
            }

            fragmentSettingsRevocation.setOnClickListener {
                activity?.apply {
                    val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_REVOCATION)
                    startActivity(intent)
                }
            }

            fragmentSettingsDataPolicy.setOnClickListener {
                activity?.apply {
                    val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_DATA_POLICY)
                    startActivity(intent)
                }
            }

            fragmentSettingsTextSize.apply {
                settingsTextDecrease.setOnClickListener {
                    decreaseFontSize()
                }
                settingsTextIncrease.setOnClickListener {
                    increaseFontSize()
                }
                settingsTextSize.setOnClickListener {
                    resetFontSize()
                }
            }
            fragmentSettingsGeneralKeepIssues.apply {
                settingsKeepLessIssues.setOnClickListener {
                    decreaseAmountOfIssues()

                }
                settingsKeepMoreIssues.setOnClickListener {
                    increaseAmountOfIssues()
                }
            }
            fragmentSettingsStorageLocation.root.setOnClickListener {
                StorageSelectionDialog(requireContext()).show()
            }
            fragmentSettingsTextJustified.setOnCheckedChangeListener { _, isChecked ->
                setTextJustification(isChecked)
            }


            // FIXME (johannes): hotfix for https://redmine.hal.taz.de/issues/14333 to prevent an infinite loop
            // when the settings are opened from an article "TextSettingsFragment"
            // There seems to be a race condition when there are multiple observers on tazApiCssDataStore.nightMode
            // - especially when the "WebViewFragment" is still active in the background, as it does re-create the
            // whole webview.
            // By using a onClickListener instead of a onCheckedChangeListener we are only listening
            // for actual user interaction. If the status is changes programmatically due to the observer
            // it wont trigger any action.
            fragmentSettingsNightMode.setOnClickListener {
                if (fragmentSettingsNightMode.isChecked) {
                    enableNightMode()
                } else {
                    disableNightMode()
                }
            }

            fragmentSettingsTapToScroll.setOnCheckedChangeListener { _, isChecked ->
                setTapToScroll(isChecked)
            }

            fragmentSettingsKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
                setKeepScreenOn(isChecked)
            }

            fragmentSettingsAccountElapsed.setOnClickListener {
                SubscriptionElapsedBottomSheetFragment().show(
                    childFragmentManager,
                    "showSubscriptionElapsed"
                )
            }

            fragmentSettingsAccountLogout.setOnClickListener {
                fragmentSettingsAccountElapsedWrapper.visibility = View.GONE
                logout()
            }

            fragmentSettingsAccountDelete.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val result = apiService.cancellation()
                        if (result != null) {
                            showCancellationDialog(result)
                        }
                    } catch (e: ConnectivityException) {
                        toastHelper.showToast(
                            resources.getString(R.string.settings_dialog_cancellation_try_later_offline_toast)
                        )
                    }
                }
            }

            val graphQlFlavorString = if (FLAVOR_graphql == "staging") {
                "-staging"
            } else {
                ""
            }

            fragmentSettingsVersionNumber.text =
                getString(
                    R.string.settings_version_number,
                    BuildConfig.VERSION_NAME,
                    graphQlFlavorString
                )

            fragmentSettingsAutoDownloadWifiSwitch.setOnCheckedChangeListener { _, isChecked ->
                setDownloadOnlyInWifi(isChecked)
            }

            fragmentSettingsAutoDownloadSwitch.setOnCheckedChangeListener { _, isChecked ->
                setDownloadEnabled(isChecked)
            }

            fragmentSettingsAutoPdfDownloadSwitch.setOnCheckedChangeListener { _, isChecked ->
                setPdfDownloadEnabled(isChecked)
            }

            if (FLAVOR_source == "nonfree") {
                fragmentSettingsNotificationsSwitchWrapper.visibility = View.VISIBLE
                fragmentSettingsNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
                    setNotificationsEnabled(isChecked)
                }
            }

            fragmentSettingsDeleteAllIssues.setOnClickListener {
                showDeleteAllIssuesDialog()
            }

            fragmentSettingsResetApp.setOnClickListener {
                showResetAppDialog()
            }

            fragmentSettingsCategoryExtended.setOnClickListener {
                toggleExtendedContent()
            }
        }


        viewModel.apply {
            fontSizeLiveData.observeDistinct(viewLifecycleOwner) { textSize ->
                textSize.toIntOrNull()?.let { textSizeInt ->
                    showFontSize(textSizeInt)
                }
            }
            textJustificationLiveData.observeDistinct(viewLifecycleOwner) { justified ->
                showTextJustification(justified)
            }
            nightModeLiveData.observeDistinct(viewLifecycleOwner) { nightMode ->
                showNightMode(nightMode)
            }
            tapToScrollLiveData.observeDistinct(viewLifecycleOwner) { enabled ->
                showTapToScroll(enabled)
            }
            keepScreenOnLiveData.observeDistinct(viewLifecycleOwner) { screenOn ->
                showKeepScreenOn(screenOn)
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
            downloadAdditionallyPdf.observeDistinct(viewLifecycleOwner) { additionallyEnabled ->
                showDownloadAdditionallyPdf(additionallyEnabled)
            }
            notificationsEnabledLivedata.observeDistinct(viewLifecycleOwner) { notificationsEnabled ->
                showNotificationsEnabledToggle(notificationsEnabled)
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

        val authHelper = AuthHelper.getInstance(requireContext().applicationContext)
        authHelper.status.asLiveData().observeDistinct(viewLifecycleOwner) { authStatus ->
            if (authStatus in arrayOf(
                    AuthStatus.valid,
                    AuthStatus.elapsed
                )
            ) {
                showLogoutButton()
                showElapsedIndication(authStatus == AuthStatus.elapsed)
            } else {
                showManageAccountButton()
            }
        }
        authHelper.email.asLiveData().observeDistinct(viewLifecycleOwner) { email ->
            viewBinding.fragmentSettingsAccountLogout.text =
                getString(R.string.settings_account_logout, email)
            // show account deletion button only when is proper email or ID (abo id which consists of just up to 6 numbers)
            if ((Pattern.compile(W3C_EMAIL_PATTERN).matcher(email)
                    .matches() || email.toIntOrNull() != null)
            ) {
                viewBinding.fragmentSettingsAccountDeleteWrapper.visibility = View.VISIBLE
            } else {
                viewBinding.fragmentSettingsAccountDeleteWrapper.visibility = View.GONE
            }
        }
    }

    private fun showDeleteAllIssuesDialog() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_settings_delete_all_issues, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel_button, null)
            .create()
        dialog.show()
        var deletionJob: Job? = null
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            it.isEnabled = false
            dialog.setCancelable(false)
            if (deletionJob == null) {
                // TODO run delete job on applicationScope but update dialogView only on lifecycle
                deletionJob = applicationScope.launch {
                    deleteAllIssuesWithProgressBar(dialogView)
                    dialog.dismiss()
                }
            }
        }
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        negativeButton.setOnClickListener {
            deletionJob?.let {
                val hint = "deleteAllIssues job was cancelled"
                log.warn(hint)
                it.cancel(hint)
            }
            dialog.dismiss()
        }
    }

    private suspend fun deleteAllIssuesWithProgressBar(
        dialogView: View
    ) = withContext(Dispatchers.Main) {
        var counter = 0
        val deletionProgress =
            dialogView.findViewById<ProgressBar>(R.id.fragment_settings_delete_progress)
        val deletionProgressText =
            dialogView.findViewById<TextView>(R.id.fragment_settings_delete_progress_text)
        val downloadedIssueStubList =
            issueRepository.getAllDownloadedIssueStubs()

        val feedName = downloadedIssueStubList.firstOrNull()?.feedName ?: BuildConfig.DISPLAYED_FEED

        deletionProgress.visibility = View.VISIBLE
        deletionProgress.progress = 0
        deletionProgress.max = downloadedIssueStubList.size
        deletionProgressText.visibility = View.VISIBLE
        deletionProgressText.text = getString(
            R.string.settings_delete_progress_text,
            counter,
            deletionProgress.max
        )

        for (issueStub in downloadedIssueStubList) {
            try {
                contentService.deleteIssue(issueStub.issueKey)
                counter++
                deletionProgress.progress = counter
                deletionProgressText.text = getString(
                    R.string.settings_delete_progress_text,
                    counter,
                    deletionProgress.max
                )
            } catch (e: CacheOperationFailedException) {
                val hint = "Error while deleting ${issueStub.issueKey}"
                log.error(hint)
                e.printStackTrace()
                Sentry.captureException(e, hint)
                toastHelper.showSomethingWentWrongToast()
                break
            }
        }
        // clean up file system:
        storageService.deleteAllUnusedIssueFolders(feedName)
        toastHelper.showToast(R.string.settings_delete_all_issues_deleted)
    }

    // region cancellation dialogs
    private fun showCancellationDialog(status: CancellationStatus) {
        if (status.canceled) {
            showAlreadyCancelled()
        } else {
            when (status.info) {
                CancellationInfo.tazId -> showCancelWithTazIdDialog(status.cancellationLink)
                CancellationInfo.aboId -> showCancelWithAboIdDialog()
                CancellationInfo.specialAccess ->
                    showCancellationDialogWithMessage(
                        getString(R.string.settings_dialog_cancellation_not_possible)
                    )
                else ->
                    showCancellationDialogWithMessage(
                        getString(R.string.settings_dialog_cancellation_something_wrong)
                    )
            }
        }
    }

    private fun showAlreadyCancelled() {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.settings_dialog_cancellation_already_canceled_title)
                .setMessage(R.string.settings_dialog_cancellation_already_canceled_description)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()
                .show()
        }
    }

    private fun showCancellationDialogWithMessage(message: String) {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()
                .show()
        }
    }

    private fun showCancelWithTazIdDialog(link: String?) {
        if (link == null) {
            toastHelper.showToast(getString(R.string.something_went_wrong_try_later))
            return
        }
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.settings_dialog_cancellation_taz_id_title)
                .setPositiveButton(R.string.settings_dialog_cancellation_open_website) { dialog, _ ->
                    openCancellationExternalPage(link)
                    (dialog as AlertDialog).hide()
                }
                .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()
                .show()
        }
    }

    private fun showCancelWithAboIdDialog() {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.settings_dialog_cancellation_are_you_sure_title)
                .setMessage(R.string.settings_dialog_cancellation_direct)
                .setPositiveButton(R.string.settings_dialog_cancellation_delete_account) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                    lifecycleScope.launch {
                        apiService.cancellation(isForce = true)
                    }
                }
                .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()
                .show()
        }
    }

    // endregion

    private fun showResetAppDialog() {
        context?.let {
            val dialog = MaterialAlertDialogBuilder(it)
                .setTitle(getString(R.string.settings_reset_app_dialog_title))
                .setMessage(getString(R.string.settings_reset_app_dialog_message))
                .setPositiveButton(getString(R.string.settings_reset_app_dialog_positive_button)) { dialog, _ ->
                    resetApp()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            dialog.show()
        }
    }

    private fun resetApp() {
        (requireContext().getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .clearApplicationUserData()
    }

    private fun showStoredIssueNumber(number: Int) {
        view?.findViewById<TextView>(R.id.settings_keep_issues)?.text = number.toString()
    }

    private fun showElapsedIndication(elapsed: Boolean) {
        if (elapsed) {
            viewModel.elapsedString.observe(viewLifecycleOwner) { elapsedOn ->
                val elapsedOnText = HtmlCompat.fromHtml(
                    elapsedOn?.let {
                        getString(R.string.settings_account_elapsed_on, it)
                    } ?: getString(R.string.settings_account_elapsed),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
                viewBinding.fragmentSettingsAccountElapsed.text = elapsedOnText
            }
            viewBinding.fragmentSettingsAccountElapsedWrapper.visibility = View.VISIBLE
        } else {
            viewBinding.fragmentSettingsAccountElapsedWrapper.visibility = View.GONE
        }
    }

    private fun showTextJustification(justified: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_text_justified)?.isChecked =
            justified
    }

    private fun showNightMode(nightMode: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_night_mode)?.isChecked = nightMode
    }

    private fun showTapToScroll(enabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_tap_to_scroll)?.isChecked = enabled
    }

    private fun showKeepScreenOn(screenOn: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_keep_screen_on)?.isChecked = screenOn
    }

    private fun showOnlyWifi(onlyWifi: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_download_wifi_switch)?.isChecked =
            onlyWifi
    }

    private fun showDownloadsEnabled(downloadsEnabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_download_switch)?.isChecked =
            downloadsEnabled
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_download_wifi_switch)?.apply {
            if (!downloadsEnabled) setDownloadOnlyInWifi(false)
            isEnabled = downloadsEnabled
        }
    }

    private fun showDownloadAdditionallyPdf(additionallyEnabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_pdf_download_switch)?.apply {
            isChecked = additionallyEnabled
        }
    }

    private fun showNotificationsEnabledToggle(notificationsEnabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_notifications_switch)?.apply {
            isChecked = notificationsEnabled
        }
    }

    private fun showFontSize(textSize: Int) {
        view?.findViewById<TextView>(
            R.id.settings_text_size
        )?.text = getString(R.string.percentage, textSize)
    }

    private fun showLogoutButton() = viewBinding.apply {
        fragmentSettingsAccountLogoutWrapper.visibility = View.VISIBLE
        fragmentSettingsAccountManageAccountWrapper.visibility = View.GONE
    }

    private fun showManageAccountButton() = viewBinding.apply {
        fragmentSettingsAccountLogoutWrapper.visibility = View.GONE
        fragmentSettingsAccountDeleteWrapper.visibility = View.GONE
        fragmentSettingsAccountManageAccountWrapper.visibility = View.VISIBLE
    }

    private fun disableNightMode() {
        log.debug("disableNightMode")
        viewModel.setNightMode(false)
    }

    private fun enableNightMode() {
        log.debug("enableNightMode")
        viewModel.setNightMode(true)
    }

    private fun setTapToScroll(enabled: Boolean) {
        log.debug("setTapToScroll: $enabled")
        viewModel.setTapToScroll(enabled)
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        viewModel.setKeepScreenOn(enabled)
    }

    private fun setTextJustification(justified: Boolean) {
        log.debug("setTextJustification to $justified")
        viewModel.setTextJustification(justified)
    }

    private fun decreaseFontSize() {
        viewModel.decreaseFontSize()
    }

    private fun increaseFontSize() {
        log.debug("increaseFontSize")
        viewModel.increaseFontSize()
    }

    private fun resetFontSize() {
        log.debug("resetFontSize")
        viewModel.resetFontSize()

    }

    private fun increaseAmountOfIssues() {
        viewModel.increaseKeepIssueNumber()
    }

    private fun decreaseAmountOfIssues() {
        viewModel.decreaseKeepIssueNumber()
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

    private fun setPdfDownloadEnabled(downloadEnabled: Boolean) {
        viewModel.setPdfDownloadsEnabled(downloadEnabled)
    }

    private fun setNotificationsEnabled(notificationsEnabled: Boolean) {
        val permissionNotSet =
            !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

        // Check if android allow the app to display notifications:
        if (notificationsEnabled && permissionNotSet) {
            showNotificationsMustBeAllowedDialog()
            showNotificationsEnabledToggle(false)
        } else {
            lifecycleScope.launch {
                val result = viewModel.setNotificationsEnabled(notificationsEnabled)
                if (result != notificationsEnabled) {
                    showNotificationsChangeErrorToast()
                    showNotificationsEnabledToggle(result)
                }
            }
        }
    }

    private fun toggleExtendedContent() {
        if (viewBinding.fragmentSettingsExtendedContent.visibility == View.GONE) {
            log.debug("show extended settings")
            viewBinding.fragmentSettingsExtendedContent.visibility = View.VISIBLE
            viewBinding.fragmentSettingsExtendedIndicator.rotation = 180f
        } else {
            log.debug("hide extended settings")
            viewBinding.fragmentSettingsExtendedContent.visibility = View.GONE
            viewBinding.fragmentSettingsExtendedIndicator.rotation = 0f
        }
    }

    private fun showNotificationsMustBeAllowedDialog() {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setMessage(R.string.settings_dialog_notifications_must_be_enabled_title)
                .setPositiveButton(R.string.settings_dialog_notifications_must_be_enabled_positive_button) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                    openAndroidNotificationSettings()
                }.setNegativeButton(R.string.cancel_button) { dialog, _ ->
                    (dialog as AlertDialog).hide()
                }
                .create()
                .show()
        }
    }

    private fun showNotificationsChangeErrorToast() {
        toastHelper.showToast(R.string.settings_dialog_notification_change_error_toast, long = true)
    }

    private fun openAndroidNotificationSettings() {
        activity?.applicationContext?.let { context ->
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
            }
            activity?.startActivity(intent)
        }
    }


    private fun logout() = requireActivity().lifecycleScope.launch {
        val authHelper = AuthHelper.getInstance(requireContext().applicationContext)
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
            val toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
            toastHelper.showToast(R.string.toast_unknown_error)
        }
    }

    private fun openCancellationExternalPage(link: String) {
        val color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(requireContext(), Uri.parse(link)) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
            toastHelper.showToast(R.string.toast_unknown_error)
        }
    }
}