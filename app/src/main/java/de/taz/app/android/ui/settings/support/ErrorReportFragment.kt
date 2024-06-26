package de.taz.app.android.ui.settings.support

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.MAX_BYTES
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentErrorReportBinding
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.validation.EmailValidator
import de.taz.app.android.sentry.SentryWrapper
import kotlinx.coroutines.launch

@Suppress("UNUSED")
class ErrorReportFragment : BaseMainFragment<FragmentErrorReportBinding>() {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    private var base64String: String? = null
    private var uploadedFileName: String? = null
    private val emailValidator = EmailValidator()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        apiService = ApiService.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onResume() {
        super.onResume()
        tracker.trackErrorReportScreen()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.settingsHeader.fragmentHeaderDefaultTitle.setText(R.string.settings_header)

        lifecycleScope.launch {
            // read email from settings
            viewBinding.fragmentErrorReportEmail.setText(
                AuthHelper.getInstance(requireContext().applicationContext).email.get()
            )
        }

        viewBinding.fragmentErrorReportUpload.setOnClickListener {
            getImageFromGallery.launch("image/*")
        }

        viewBinding.fragmentErrorReportSendButton.setOnClickListener {
            viewBinding.loadingScreen.root.visibility = View.VISIBLE
            val email = viewBinding.fragmentErrorReportEmail.text.toString().trim()
            val message = viewBinding.fragmentErrorReportMessage.text.toString().trim()
            val lastAction = viewBinding.fragmentErrorReportLastAction.text.toString().trim()
            val conditions = viewBinding.fragmentErrorReportConditions.text.toString().trim()

            lifecycleScope.launch {
                var inputErrors = false

                if (message.isBlank()) {
                    inputErrors = true
                    viewBinding.fragmentErrorReportMessage.error =
                        requireContext().getString(R.string.login_email_message_empty)
                }

                var errorReportEmail: String? = null
                // Ignore provided mail if we are logged in
                if (!authHelper.isLoggedIn()) {
                    if (emailValidator(email)) {
                        errorReportEmail = email
                    } else {
                        inputErrors = true
                        viewBinding.fragmentErrorReportEmail.error =
                            requireContext().getString(R.string.login_email_error_empty)
                    }
                }

                if (!inputErrors) {
                    sendErrorReport(
                        errorReportEmail,
                        message,
                        lastAction,
                        conditions,
                        uploadedFileName,
                        base64String
                    )
                } else {
                    viewBinding.loadingScreen.root.visibility = View.GONE
                }
            }
        }
    }

    private val getImageFromGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val inputStream = try {
                    requireContext().contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    log.warn("Something went wrong opening input stream: ${e.localizedMessage}")
                    SentryWrapper.captureException(e)
                    null
                }

                inputStream?.let {
                    // get the bas64 encoded String:
                    val base64StringTotal =
                        Base64.encodeToString(inputStream.readBytes(), Base64.NO_WRAP)

                    // Check if string is not longer than 2^32-1 bytes:
                    if (base64StringTotal.encodeToByteArray().size < MAX_BYTES) {
                        viewBinding.fragmentErrorReportScreenshotThumbnail.setImageURI(uri)
                        base64String = base64StringTotal
                    } else {
                        toastHelper.showToast(R.string.toast_error_report_upload_file_too_big)
                    }

                    // get the filename from uri:
                    requireContext().contentResolver.query(uri, null, null, null, null)
                        .use { cursor ->
                            val nameIndex = cursor?.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME
                            )
                            cursor?.moveToFirst()
                            nameIndex?.let {
                                uploadedFileName = cursor.getString(nameIndex)
                            }
                        }
                } ?: run {
                    log.warn("input stream is null")
                    toastHelper.showToast(R.string.toast_error_report_upload_file_not_found)
                }
            } else {
                log.debug("No image from gallery selected")
            }
        }

    private fun sendErrorReport(
        email: String?,
        message: String?,
        lastAction: String?,
        conditions: String?,
        screenshotName: String?,
        screenshot: String?
    ) {
        val storageType =
            StorageService.getInstance(requireContext().applicationContext)
                .getInternalFilesDir().absolutePath
        val errorProtocol = Log.getTrace().joinToString("\n")

        val activityManager =
            this.requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem
        val usedRam = memoryInfo.totalMem - memoryInfo.availMem

        log.debug("Sending an error report")
        applicationScope.launch {
            apiService.apply {
                retryOnConnectionFailure {
                    sendErrorReport(
                        email,
                        message,
                        lastAction,
                        conditions,
                        storageType,
                        errorProtocol,
                        usedRam.toString(),
                        totalRam.toString(),
                        screenshotName,
                        screenshot
                    )
                }
            }
        }
        toastHelper.showToast(R.string.toast_error_report_sent)

        requireActivity().finish()

    }
}
