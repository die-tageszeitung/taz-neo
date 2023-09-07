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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import de.taz.app.android.*
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.CancellationInfo
import de.taz.app.android.api.models.CancellationStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.FeedService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.databinding.FragmentSettingsBinding
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.LoginContract
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.getStorageLocationCaption
import de.taz.app.android.util.validation.EmailValidator
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

private const val DEBUG_SETTINGS_REQUIRED_CLICKS = 7
private const val DEBUG_SETTINGS_MAX_CLICK_TIME_MS = 5_000L
private const val TAZ_PORTAL_LOGIN_URI = "https://portal.taz.de/user/login"

@Suppress("UNUSED")
class SettingsFragment : BaseViewModelFragment<SettingsViewModel, FragmentSettingsBinding>() {
    private val log by Log

    private var storedIssueNumber: Int? = null
    private var lastStorageLocation: StorageLocation? = null
    private var areDebugSettingsEnabled: Boolean = false
    private var enableDebugSettingsClickCount: Int = 0
    private var enableDebugSettingsFirstClickMs: Long = 0L

    private lateinit var apiService: ApiService
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository
    private lateinit var storageService: StorageService
    private lateinit var toastHelper: ToastHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var feedService: FeedService
    private lateinit var tracker: Tracker
    private val loginActivityLauncher = registerForActivityResult(LoginContract()) {}

    private val emailValidator = EmailValidator()

    private val pushNotificationsFeatureEnabled = (BuildConfig.FLAVOR_source == "nonfree")
    private val isTrackingFeatureEnabled = (BuildConfig.FLAVOR_source == "nonfree" && !BuildConfig.IS_LMD)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        feedService = FeedService.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.fragment_header_default_title)
            ?.setText(R.string.settings_header)
        viewBinding.apply {
            fragmentSettingsSupportReportBug.setOnClickListener { reportBug() }
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
                    SubscriptionElapsedBottomSheetFragment.TAG
                )
            }

            if (!BuildConfig.IS_LMD) {
                fragmentSettingsAccountResetPassword.setOnClickListener {
                    lifecycleScope.launch {
                        loginActivityLauncher.launch(
                            LoginContract.Input(
                                option = LoginContract.Option.REQUEST_PASSWORD_RESET,
                                username = authHelper.email.get()
                            )
                        )
                    }
                }

                fragmentSettingsManageAccountOnline.setOnClickListener {
                    openProfileAccountOnline()
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
            }

            fragmentSettingsAccountLogout.setOnClickListener {
                fragmentSettingsAccountElapsedWrapper.visibility = View.GONE
                logout()
            }

            val graphQlFlavorString = if (BuildConfig.FLAVOR_graphql == "staging") {
                "-staging"
            } else {
                ""
            }

            fragmentSettingsVersionNumber.apply {
                text = getString(
                    R.string.settings_version_number,
                    BuildConfig.VERSION_NAME,
                    graphQlFlavorString
                )
                setOnClickListener{ handleEnableDebugSettingsClick() }
            }

            fragmentSettingsAutoDownloadWifiSwitch.setOnCheckedChangeListener { _, isChecked ->
                setDownloadOnlyInWifi(isChecked)
            }

            fragmentSettingsAutoDownloadSwitch.setOnCheckedChangeListener { _, isChecked ->
                setDownloadEnabled(isChecked)
            }

            if (BuildConfig.IS_LMD) {
                hideAutoPdfDownloadSwitch()
            } else {
                fragmentSettingsAutoPdfDownloadSwitch.setOnCheckedChangeListener { _, isChecked ->
                    setPdfDownloadEnabled(isChecked)
                }
            }

            fragmentSettingsFaq.setOnClickListener { openFAQ() }
            if (pushNotificationsFeatureEnabled) {
                fragmentSettingsNotificationsSwitchWrapper.visibility = View.VISIBLE
                pushNotificationsAllowedLayout.visibility = View.VISIBLE
                fragmentSettingsNotificationsSwitch.setOnClickListener { _ ->
                    toggleNotificationsEnabled()
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

            if (isTrackingFeatureEnabled) {
                fragmentSettingsAcceptTrackingSwitchWrapper.visibility = View.VISIBLE
                fragmentSettingsAcceptTrackingSwitch.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setTrackingAccepted(isChecked)
                }
            }
        }


        viewModel.apply {
            fontSizeLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { textSize ->
                textSize.toIntOrNull()?.let { textSizeInt ->
                    showFontSize(textSizeInt)
                }
            }
            textJustificationLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { justified ->
                showTextJustification(justified)
            }
            nightModeLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { nightMode ->
                showNightMode(nightMode)
            }
            tapToScrollLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { enabled ->
                showTapToScroll(enabled)
            }
            keepScreenOnLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { screenOn ->
                showKeepScreenOn(screenOn)
            }
            storedIssueNumberLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { storedIssueNumber ->
                showStoredIssueNumber(storedIssueNumber)
            }
            downloadOnlyWifiLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { onlyWifi ->
                showOnlyWifi(onlyWifi)
            }
            downloadAutomaticallyLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { downloadsEnabled ->
                showDownloadsEnabled(downloadsEnabled)
            }
            downloadAdditionallyPdf.distinctUntilChanged().observe(viewLifecycleOwner) { additionallyEnabled ->
                showDownloadAdditionallyPdf(additionallyEnabled)
            }
            trackingAccepted.distinctUntilChanged().observe(viewLifecycleOwner) { isTrackingAccepted ->
                viewBinding.fragmentSettingsAcceptTrackingSwitch.isChecked = isTrackingAccepted
            }
            if (pushNotificationsFeatureEnabled) {
                notificationsEnabledLivedata.distinctUntilChanged()
                    .observe(viewLifecycleOwner) { notificationsEnabled ->
                        updateNotificationViews(notificationsEnabled, systemNotificationsAllowed())
                    }
            }
            storageLocationLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { storageLocation ->
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
            elapsedString.observe(viewLifecycleOwner) {
                setElapsedString(it)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val authStatusFlow: Flow<AuthStatus> = authHelper.status.asFlow()
                val authEmailFlow: Flow<String> = authHelper.email.asFlow()

                combine(authStatusFlow, authEmailFlow) { authStatus, email -> authStatus to email  }
                    .distinctUntilChanged()
                    .collect { (authStatus, email) ->
                        val isLoggedIn = authStatus in arrayOf(AuthStatus.valid, AuthStatus.elapsed)
                        val isElapsed = authStatus == AuthStatus.elapsed
                        val isValidEmail = emailValidator(email)
                        val isAboId = email.toIntOrNull() != null
                        // taz account is not the same as taz id. it means accounts from taz workers!
                        val isTazAccount = email.endsWith(TAZ_ACCOUNT_SUFFIX)

                        // Show the views for logged in also when we have a valid email, s
                        // this happens when a user creates a Probeabo with a new account and the email verification is pending
                        if (isLoggedIn || isValidEmail) {
                            viewBinding.fragmentSettingsAccountLogout.text =
                                getString(R.string.settings_account_logout, email)
                            showActionsWhenLoggedIn(isValidEmail, isAboId, isTazAccount)
                        } else {
                            showLoginButton()
                        }
                        showElapsedIndication(isElapsed)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                setupDebugSettings()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pushNotificationsFeatureEnabled) {
            lifecycleScope.launch {
                checkNotificationsAllowed()
            }
        }
        tracker.trackSettingsScreen()
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

    private fun hideAutoPdfDownloadSwitch() {
        viewBinding.fragmentSettingsAutoPdfDownloadSwitch.visibility = View.GONE
        viewBinding.fragmentSettingsAutoDownloadSwitchSeparatorLine.root.visibility = View.GONE
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
        deletionProgressText.text = getString(R.string.settings_delete_progress_text)

        for (issueStub in downloadedIssueStubList) {
            try {
                contentService.deleteIssue(issueStub.issueKey)
                counter++
                deletionProgress.progress = counter
            } catch (e: CacheOperationFailedException) {
                log.warn("Error while deleting ${issueStub.issueKey}", e)
                Sentry.captureException(e)
                toastHelper.showSomethingWentWrongToast()
                break
            }
        }
        // clean up file system:
        //FIXME: Check why the moments are not deleted together with the issues!
        // Until we know that we cannot delete the "unused" folders. So this is only a quickfix:
        //storageService.deleteAllUnusedIssueFolders(feedName)
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
            viewBinding.fragmentSettingsAccountElapsedWrapper.visibility = View.VISIBLE
        } else {
            viewBinding.fragmentSettingsAccountElapsedWrapper.visibility = View.GONE
        }
    }

    private fun setElapsedString(elapsedOn: String?) {
        val elapsedOnText = HtmlCompat.fromHtml(
            elapsedOn?.let {
                getString(R.string.settings_account_elapsed_on, it)
            } ?: getString(R.string.settings_account_elapsed),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        viewBinding.fragmentSettingsAccountElapsed.text = elapsedOnText
    }

    private fun showTextJustification(justified: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_text_justified)?.isChecked =
            justified
    }

    private fun showNightMode(nightMode: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_night_mode)?.isChecked = nightMode
    }

    private fun showTapToScroll(enabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_tap_to_scroll)?.isChecked =
            enabled
    }

    private fun showKeepScreenOn(screenOn: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_keep_screen_on)?.isChecked =
            screenOn
    }

    private fun showOnlyWifi(onlyWifi: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_download_wifi_switch)?.isChecked =
            onlyWifi
    }

    private fun showDownloadsEnabled(downloadsEnabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_download_switch)?.isChecked =
            downloadsEnabled
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_download_wifi_switch)
            ?.apply {
                if (!downloadsEnabled) setDownloadOnlyInWifi(false)
                isEnabled = downloadsEnabled
            }
    }

    private fun showDownloadAdditionallyPdf(additionallyEnabled: Boolean) {
        view?.findViewById<MaterialSwitch>(R.id.fragment_settings_auto_pdf_download_switch)?.apply {
            isChecked = additionallyEnabled
        }
    }

    private fun showFontSize(textSize: Int) {
        view?.findViewById<TextView>(
            R.id.settings_text_size
        )?.text = getString(R.string.percentage, textSize)
    }

    private fun showLoginButton() = viewBinding.apply {
        fragmentSettingsAccountLogoutWrapper.visibility = View.GONE
        fragmentSettingsAccountResetPasswordWrapper.visibility = View.GONE
        fragmentSettingsManageAccountOnlineWrapper.visibility = View.GONE
        fragmentSettingsAccountDeleteWrapper.visibility = View.GONE
        fragmentSettingsAccountManageAccountWrapper.visibility = View.VISIBLE
    }

    private fun showActionsWhenLoggedIn(
        isValidEmail: Boolean = false,
        isAboId: Boolean = false,
        isTazAccount: Boolean = false
    ) = viewBinding.apply {
        fragmentSettingsAccountManageAccountWrapper.visibility = View.GONE
        if (!BuildConfig.IS_LMD) {
            fragmentSettingsManageAccountOnlineWrapper.visibility = View.VISIBLE
            // show account deletion button only when is proper email or ID (abo id which consists of just up to 6 numbers)
            fragmentSettingsAccountDeleteWrapper.visibility =
                if ((isValidEmail || isAboId) && !isTazAccount) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            // show reset password option only for when we have a valid mail:
            fragmentSettingsAccountResetPasswordWrapper.visibility =
                if (isValidEmail && !isTazAccount) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
        fragmentSettingsAccountLogoutWrapper.visibility = View.VISIBLE
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

    private fun setTrackingAccepted(isAccepted: Boolean) {
        viewModel.setTrackingAccepted(isAccepted)
    }

    /**
     * Called when the user clicked the notification toggle.
     * Note that Android will still change the toggle button state itself,
     * thus we have to reset the toggle state on errors manually.
     */
    private fun toggleNotificationsEnabled() {
        lifecycleScope.launch {
            val areAppNotificationsEnabled = viewModel.getNotificationsEnabled()
            val toggledNotification = !areAppNotificationsEnabled

            val result = viewModel.setNotificationsEnabled(!areAppNotificationsEnabled)

            if (result != toggledNotification) {
                showNotificationsChangeErrorToast()
            }
            updateNotificationViews(result, systemNotificationsAllowed())
        }
    }

    /**
     * Checks if the system notifications are allowed by system
     * and show depending on the viewModel's settings some layout.
     */
    private suspend fun checkNotificationsAllowed() {
        updateNotificationViews(viewModel.getNotificationsEnabled(), systemNotificationsAllowed())
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

    private fun updateNotificationViews(notificationsEnabled: Boolean, systemNotificationsAllowed: Boolean) {
        viewBinding.fragmentSettingsNotificationsSwitch.isChecked = notificationsEnabled
        if (!systemNotificationsAllowed) {
            if (notificationsEnabled) {
                showNotificationsMustBeAllowedLayout(show = true)
            } else {
                showNotificationsShouldBeAllowedLayout(show = true)
            }
        } else {
            showNotificationsShouldBeAllowedLayout(show = false)
            showNotificationsMustBeAllowedLayout(show = false)
            showNotificationsAllowedLayout(show = true)
        }
    }

    /**
     * Toggle text indicating that notifications must be allowed when they are enabled at the moment
     */
    private fun showNotificationsMustBeAllowedLayout(show: Boolean) {
        if (show) {
            showNotificationsShouldBeAllowedLayout(show = false)
            showNotificationsAllowedLayout(show = false)
            viewBinding.pushNotificationsMustBeAllowedLayout.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    openAndroidNotificationSettings()
                }
            }
            viewBinding.fragmentSettingsNotificationsSwitch.trackTintList =
                ContextCompat.getColorStateList(
                    requireContext(),
                    R.color.material_switch_disabled_color_list
                )
        } else {
            viewBinding.fragmentSettingsNotificationsSwitch.trackTintList =
                ContextCompat.getColorStateList(
                    requireContext(),
                    R.color.material_switch_color_list
                )
            viewBinding.pushNotificationsMustBeAllowedLayout.visibility = View.GONE
        }
    }

    /**
     * Toggle text indicating that notifications hall be allowed when they are disabled at the moment
     */
    private fun showNotificationsShouldBeAllowedLayout(show: Boolean) {
        if (show) {
            showNotificationsMustBeAllowedLayout(show = false)
            showNotificationsAllowedLayout(show = false)
            viewBinding.pushNotificationsShouldBeAllowedLayout.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    openAndroidNotificationSettings()
                }
            }
        } else {
            viewBinding.pushNotificationsShouldBeAllowedLayout.visibility = View.GONE
        }
    }

    /**
     * Toggle text indicating that notifications are good to have
     */
    private fun showNotificationsAllowedLayout(show: Boolean) {
        if (show) {
            showNotificationsShouldBeAllowedLayout(show = false)
            showNotificationsMustBeAllowedLayout(show = false)
            viewBinding.pushNotificationsAllowedLayout.visibility = View.VISIBLE
        } else {
            viewBinding.pushNotificationsAllowedLayout.visibility = View.GONE
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
        authHelper.status.set(AuthStatus.notValid)
        getApplicationScope().launch {
            // Refresh the feed in the background to show all public issues again when the user was logged in as a wochentaz user
            try {
                feedService.refreshFeed(BuildConfig.DISPLAYED_FEED)
            } catch (e: ConnectivityException) {
                log.error("Could not refresh the feed after logout", e)
            }
        }
    }

    private fun openFAQ() {
        val color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        try {
            val faqUri = Uri.parse(getString(R.string.faq_link))
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(requireContext(), faqUri) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
            toastHelper.showToast(R.string.toast_unknown_error)
        }
    }

    private fun openProfileAccountOnline() {
        val color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        lifecycleScope.launch {
            val mail = authHelper.email.get()
            val isValidMail = emailValidator(mail)
            val uri = if (isValidMail) {
                "$TAZ_PORTAL_LOGIN_URI?email=$mail"
            } else {
                TAZ_PORTAL_LOGIN_URI
            }
            try {
                CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(
                        CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                    )
                    .build()
                    .apply {
                        launchUrl(
                            requireContext(),
                            Uri.parse(uri)
                        )
                    }
            } catch (e: ActivityNotFoundException) {
                val toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
                toastHelper.showToast(R.string.toast_unknown_error)
            }
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

    private fun systemNotificationsAllowed() =
        NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()


    private fun handleEnableDebugSettingsClick() {
        if (!areDebugSettingsEnabled) {
            val now = System.currentTimeMillis()

            if (enableDebugSettingsFirstClickMs + DEBUG_SETTINGS_MAX_CLICK_TIME_MS < now) {
                enableDebugSettingsClickCount = 1
                enableDebugSettingsFirstClickMs = now
            } else {
                enableDebugSettingsClickCount++
            }

            if (enableDebugSettingsClickCount >= DEBUG_SETTINGS_REQUIRED_CLICKS) {
                viewModel.enableDebugSettings()
                toastHelper.showToast(R.string.toast_debug_settings_enabled)
                areDebugSettingsEnabled = true
                setupDebugSettings()
            }
        }
    }

    private fun setupDebugSettings() {
        lifecycleScope.launch {
            if (viewModel.areDebugSettingsEnabled()) {
                areDebugSettingsEnabled = true
                viewBinding.apply {
                    fragmentSettingsDebugSettings.isVisible = true

                    fragmentSettingsForceNewAppSession.apply {
                        text = getString(
                            R.string.settings_debug_force_new_app_session,
                            viewModel.getAppSessionCount()
                        )
                        setOnClickListener {
                            toastHelper.showToast(R.string.toast_force_new_app_session)
                            viewModel.forceNewAppSession()
                        }
                    }

                }
            }
        }
    }
}