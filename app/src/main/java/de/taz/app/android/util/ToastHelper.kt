package de.taz.app.android.util

import android.content.Context
import android.os.Handler
import android.widget.Toast
import de.taz.app.android.R
import java.util.*

/**
 * Singleton to create Toasts
 */
class ToastHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<ToastHelper, Context>(::ToastHelper)

    fun makeToast(messageId: Int, long: Boolean = false) {
        makeToast(applicationContext.resources.getString(messageId), long)
    }

    fun makeToast(message: String, long: Boolean = false) {
        val mainHandler = Handler(applicationContext.mainLooper)

        val toastDuration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT

        val myRunnable = Runnable {
            Toast.makeText(applicationContext, message, toastDuration).show()
        }
        mainHandler.post(myRunnable)
    }

    private var lastConnectionError = Date().time

    fun showNoConnectionToast() {
        val now = Date().time
        if (now > lastConnectionError + 10000) {
            lastConnectionError = now
            makeToast(R.string.toast_no_internet)
        }
    }


}