package de.taz.app.android.util

import android.content.Context
import android.os.Environment
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.FileEntryRepository
import kotlinx.io.IOException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

open class FileHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<FileHelper, Context>(::FileHelper)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun createFile(fileEntry: FileEntry): Boolean {
        createFileDirs(fileEntry)
        return getFile(fileEntry).createNewFile()
    }

    fun createFileDirs(fileEntry: FileEntry): Boolean {
        return getDir(fileEntry).mkdirs()
    }

    @UiThread
    fun deleteFile(fileName: String): Boolean {
        return fileEntryRepository.get(fileName)?.let { fileEntry ->
            deleteFile(fileEntry)
        } ?: false
    }

    fun deleteFile(fileEntry: FileEntry): Boolean {
        return getFile(fileEntry).delete()
    }

    fun getDir(fileEntry: FileEntry): File {
        return getFileByPath(fileEntry.folder)
    }

    @UiThread
    fun getFile(fileEntryName: String): File? {
        return fileEntryRepository.get(fileEntryName)?.let { getFile(it) }
    }

    fun getFile(fileEntry: FileEntry): File {
        return getFileByPath(fileEntry.path)
    }

    fun getFileByPath(filePath: String, internal: Boolean = false): File {
        // TODO read from settings where to save
        // TODO notification if external not writable?
        return if (internal || !isExternalStorageWritable())
            File(applicationContext.filesDir, filePath)
        else {
            return File(ContextCompat.getExternalFilesDirs(applicationContext, null).first(), filePath)
        }
    }

    fun getFileDirectoryUrl(context: Context, internal: Boolean = false) : String {
        context.applicationContext.let {
            return if (internal)
                "file://${it.filesDir.absolutePath}"
            else
                "file://${ContextCompat.getExternalFilesDirs(it,null).first().absolutePath}"
        }

    }

    fun getFilesDir(context: Context) : String {
        return ContextCompat.getExternalFilesDirs(context, null).first().absolutePath
    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    @UiThread
    @Throws(IOException::class)
    fun readFileFromAssets(path: String): String {
        var bufferedReader: BufferedReader? = null
        var data = ""
        try {
            bufferedReader = BufferedReader(
                InputStreamReader(
                    applicationContext.assets.open(path),
                    "UTF-8"
                )
            )

            var line: String? = bufferedReader.readLine()
            while (line != null) {
                data += line
                line = bufferedReader.readLine()
            }
        } finally {
            bufferedReader?.close()
        }
        return data
    }
}