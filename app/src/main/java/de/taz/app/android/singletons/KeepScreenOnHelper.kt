package de.taz.app.android.singletons

import android.content.Context
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeepScreenOnHelper private constructor(private val applicationContext: Context) {
    companion object: SingletonHolder<KeepScreenOnHelper, Context>(::KeepScreenOnHelper)

    private val log by Log

    suspend fun toggleScreenOn(
        keepScreenOn: Boolean,
        activity: FragmentActivity?
    ) = withContext(Dispatchers.Main) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            log.debug("Screen on")
        }
        else {
            log.debug("Screen off")
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}