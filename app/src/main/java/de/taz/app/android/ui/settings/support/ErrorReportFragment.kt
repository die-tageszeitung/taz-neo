package de.taz.app.android.ui.settings.support

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import de.taz.app.android.MAX_BYTES
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.singletons.*
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.android.synthetic.main.fragment_error_report.*
import kotlinx.android.synthetic.main.fragment_header_default.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.*


class ErrorReportFragment : BaseMainFragment(R.layout.fragment_error_report) {

    private val log by Log

    private lateinit var apiService: ApiService
    private lateinit var toastHelper: ToastHelper
    var base64String: String? = null
    var uploadedFileName: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
        apiService = ApiService.getInstance(requireContext().applicationContext)

    }

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

            fragment_error_report_upload.setOnClickListener(View.OnClickListener {
                getImageFromGallery.launch("image/*")
            })

            fragment_error_report_send_button.setOnClickListener {
                loading_screen.visibility = View.VISIBLE
                val email = fragment_error_report_email.text.toString().trim()
                val message = fragment_error_report_message.text.toString().trim()
                val lastAction = fragment_error_report_last_action.text.toString().trim()
                val conditions = fragment_error_report_conditions.text.toString().trim()

                if (email.isNotEmpty() || message.isNotEmpty() || lastAction.isNotEmpty() || conditions.isNotEmpty()) {
                    sendErrorReport(
                        email,
                        message,
                        lastAction,
                        conditions,
                        uploadedFileName,
                        base64String
                    )
                } else {
                    loading_screen.visibility = View.GONE
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
                    Sentry.captureException(e)
                    null
                }

                inputStream?.let {
                    // get the bas64 encoded String:
                    val base64StringTotal =
                        Base64.encodeToString(inputStream.readBytes(), Base64.NO_WRAP)

                    // Check if string is not longer than 2^32-1 bytes:
                    if (base64StringTotal.encodeToByteArray().size < MAX_BYTES) {
                        fragment_error_report_screenshot_thumbnail.setImageURI(uri)
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
            StorageService.getInstance(activity?.applicationContext).getInternalFilesDir().absolutePath
        val errorProtocol = Log.trace.toString()

        val activityManager =
            this.requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = "%.2f GB".format(memoryInfo.totalMem / 1073741824f)
        val usedRam =
            "%.2f GB".format((memoryInfo.totalMem - memoryInfo.availMem) / 1073741824f)

        CoroutineScope(Dispatchers.IO).launch {
            log.debug("Sending an error report")
            apiService.apply {
                retryOnConnectionFailure {
                    sendErrorReport(
                        email,
                        message,
                        lastAction,
                        conditions,
                        storageType,
                        errorProtocol,
                        usedRam,
                        totalRam,
                        screenshotName,
                        screenshot
                    )
                }
            }
            toastHelper.showToast(R.string.toast_error_report_sent)
        }
        requireActivity().finish()

    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            log.debug("Show home clicked")
            requireActivity().finish()
        }
    }
}
