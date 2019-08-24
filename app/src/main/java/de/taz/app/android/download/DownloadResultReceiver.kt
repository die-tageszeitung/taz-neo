package de.taz.app.android.download

import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper

const val RESULT_OK = 1
const val RESULT_ABORTED = 2

const val BUNDLE_DOWNLOAD = "bundle.download"

class DownloadResultReceiver : ResultReceiver(Handler()) {

    private val log by Log

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        log.debug("received result - resultCode $resultCode resultData: $resultData")
        super.onReceiveResult(resultCode, resultData)

        resultData?.getString(BUNDLE_DOWNLOAD)?.let {
            log.debug("bundle present")
            Download.deserialize(it).let { download ->
                when (resultCode) {
                    RESULT_OK -> verifyDownload(download)
                    RESULT_ABORTED -> rescheduleDownload(download)
                }
            }
        }
    }

    private fun verifyDownload(download: Download) {
        download.sha256?.run { verifySHA256(download) }
        download.size?.run { verifySize(download) }
        ToastHelper.getInstance().makeToast("${download.name} downloaded")
    }

    private fun verifySHA256(download: Download) {}
    private fun verifySize(download: Download) {}
    private fun rescheduleDownload(download: Download) {
    }

}