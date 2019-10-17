package de.taz.app.android.util

import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import okhttp3.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


/**
 * helper function to transform callback into suspend function
 */
suspend fun awaitCallback(block: (Callback) -> Unit): Response =
    suspendCancellableCoroutine { cont ->
        block(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
        })
    }
