package de.taz.app.android.util

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import de.taz.app.android.api.models.Download
import de.taz.app.android.persistence.repository.DownloadRepository
import java.io.File

class FileHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<FileHelper, Context>(::FileHelper)

    private val downloadRepository = DownloadRepository.getInstance(applicationContext)

    fun deleteFile(fileName: String): Boolean {
        return downloadRepository.get(fileName)?.let { download ->
            deleteFileForDownload(download)
        } ?: false
    }

    fun deleteFileForDownload(download: Download): Boolean {
        return getFile(download.path).delete()
    }

    fun getFile(fileName: String, internal: Boolean = false): File {
        // TODO read from settings where to save
        // TODO notification if external not writable?
        return if (internal || !isExternalStorageWritable())
            File(applicationContext.filesDir, fileName)
        else {
            return File(ContextCompat.getExternalFilesDirs(applicationContext, null).first(), fileName)
        }
    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

}