package de.taz.app.android.singletons

import android.content.Context
import android.widget.Toast
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Singleton to create Toasts
 */
@Mockable
class ToastHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<ToastHelper, Context>(::ToastHelper)

    fun showToast(messageId: Int, long: Boolean = false) {
        showToast(applicationContext.resources.getString(messageId), long)
    }

    fun showToast(message: String, long: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            val toastDuration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(applicationContext, message, toastDuration).show()
        }
    }

    private var lastConnectionError = 0L

    fun showNoConnectionToast() {
        val now = Date().time
        if (now > lastConnectionError + 10000) {
            lastConnectionError = now
            showToast(R.string.toast_no_internet)
        }
    }

    private var lastSomethingWentWrontToast = 0L
    fun showSomethingWentWrongToast() {
        val now = Date().time
        if (now > lastSomethingWentWrontToast+ 10000) {
            lastSomethingWentWrontToast = now
            showToast(R.string.something_went_wrong_try_later)
        }
    }


}