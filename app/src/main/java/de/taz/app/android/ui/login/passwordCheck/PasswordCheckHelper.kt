package de.taz.app.android.ui.login.passwordCheck

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Json
import de.taz.app.android.util.Log
import de.taz.app.android.util.validation.PasswordValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val CHECK_PASSWORD_JS_FILE = "tazPasswordSpec.js"

/**
 * Helper wrapping the password check functionality.
 * If available it will use the [CHECK_PASSWORD_JS_FILE] file shipped with the taz resources for
 * checking. If not it will fall back to the simple regex validator.
 */
class PasswordCheckHelper(private val applicationContext: Context) : CoroutineScope {

    private val log by Log
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default

    // null if the password spec file could not be loaded
    private var webView: WebView? = null

    init {
        launch { initPasswordSpecJs() }
    }

    /**
     * Try to load the [CHECK_PASSWORD_JS_FILE] and prepare the [webView] that is used to
     * check the passwords with.
     */
    private suspend fun initPasswordSpecJs() {
        val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        val storageService = StorageService.getInstance(applicationContext)

        val passwordJsFile = fileEntryRepository.get(CHECK_PASSWORD_JS_FILE)?.let {
            storageService.getFile(it)
        } ?: run {
            log.warn("No file for password check $CHECK_PASSWORD_JS_FILE found.")
            return
        }

        val passwordJsText = try {
            passwordJsFile.readText()
        } catch (e: IOException) {
            log.error("Could not read data from password spec file: $passwordJsFile")
            return
        }

        // The WebView instance has to be created on a Thread which has a Looper, like the Main/UI
        // thread, otherwise the constructor throws an exception.
        withContext(Dispatchers.Main) {
            @SuppressLint("SetJavaScriptEnabled")
            webView = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                evaluateJavascript(passwordJsText, null)
            }
        }
    }

    /**
     * Check the [password] and return the result as a [PasswordHintResponse].
     * The [mail] is passed for extended validity checks.
     */
    suspend fun checkPassword(password: String, mail: String): PasswordHintResponse {
        return webView?.let {
            checkWithPasswordJs(it, password, mail)
        } ?: checkWithPasswordValidator(password)
    }

    private suspend fun checkWithPasswordJs(
        webView: WebView,
        password: String,
        mail: String
    ): PasswordHintResponse {
        val passwordEscaped = password.replace("'", "\\'")
        val mailEscaped = mail.replace("'", "\\'")
        return suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript("tazPasswordSpec.checkLocal('$passwordEscaped', '$mailEscaped');") { resultString ->
                try {
                    val result = Json.decodeFromString<PasswordHintResponse>(resultString)
                    cont.resume(result)
                } catch (e: Exception) {
                    log.error("Could not decode PasswordHintResponse from: $resultString")
                    cont.resumeWithException(e)
                }
            }
        }
    }

    private fun checkWithPasswordValidator(password: String): PasswordHintResponse {
        val passwordValidator = PasswordValidator()
        return if (passwordValidator(password)) {
            val validMessage = applicationContext.getString(R.string.login_password_regex_valid)
            PasswordHintResponse(validMessage, 3, true)
        } else {
            val errorMessage = applicationContext.getString(R.string.login_password_regex_error)
            PasswordHintResponse(errorMessage, 0, false)
        }
    }
}