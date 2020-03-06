package de.taz.app.android.singletons

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.persistence.repository.FileEntryRepository
import kotlinx.io.IOException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

@Mockable
class FileHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<FileHelper, Context>(::FileHelper)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun createFile(fileEntry: FileEntry): Boolean {
        createFileDirs(fileEntry)
        return getFile(fileEntry).createNewFile()
    }

    fun createFileDirs(fileEntry: FileEntry): Boolean {
        return getDir(fileEntry).mkdirs()
    }

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

    fun getFile(fileEntryName: String): File? {
        return fileEntryRepository.get(fileEntryName)?.let { getFile(it) }
    }

    fun getFile(fileEntry: FileEntry): File {
        return getFileByPath(fileEntry.path)
    }

    fun writeFile(fileEntry: FileEntry, byteArray: ByteArray) {
        val file = getFile(fileEntry)
        file.writeBytes(byteArray)
    }

    fun getFileByPath(filePath: String, internal: Boolean = false): File {
        // TODO read from settings where to save
        // TODO notification if external not writable?
        return if (internal || !isExternalStorageWritable())
            File(applicationContext.filesDir, filePath)
        else {
            return File(
                ContextCompat.getExternalFilesDirs(applicationContext, null).first(),
                filePath
            )
        }
    }

    fun getFileDirectoryUrl(context: Context, internal: Boolean = false): String {
        context.applicationContext.let {
            return if (internal)
                "file://${it.filesDir.absolutePath}"
            else
                "file://${ContextCompat.getExternalFilesDirs(it, null).first().absolutePath}"
        }

    }

    fun getFilesDir(context: Context): String {
        return ContextCompat.getExternalFilesDirs(context, null).first().absolutePath
    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    @Throws(IOException::class)
    fun readFileFromAssets(path: String): String {
        var bufferedReader: BufferedReader? = null
        var data = ""
        try {
            bufferedReader = assetFileReader(path)

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

    private fun assetFileReader(path: String): BufferedReader {
        return BufferedReader(
            InputStreamReader(
                applicationContext.assets.open(path),
                "UTF-8"
            )
        )
    }

    fun assetFileSameContentAsFile(assetFilePath: String, file: File): Boolean {
        val assetBufferedReader = assetFileReader(assetFilePath)
        val fileBufferedReader = file.bufferedReader()

        var areEqual = true

        try {
            var line = assetBufferedReader.readLine()
            var otherLine = fileBufferedReader.readLine()

            while (line != null || otherLine != null) {
                if (line == null || otherLine == null) {
                    areEqual = false
                    break
                } else if (line != otherLine) {
                    areEqual = false;
                    break
                }

                line = assetBufferedReader.readLine()
                otherLine = fileBufferedReader.readLine()
            }
        } finally {
            assetBufferedReader.close()
            fileBufferedReader.close()
        }

        return areEqual
    }

    fun copyAssetFileToFile(path: String, file: File) {
        val tazApiAssetReader = assetFileReader(path)
        val fileWriter = file.writer()
        tazApiAssetReader.copyTo(fileWriter)
        tazApiAssetReader.close()
        fileWriter.close()
    }

}