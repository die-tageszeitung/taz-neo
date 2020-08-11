package de.taz.app.android.singletons

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.SingletonHolder
import okio.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.net.ssl.SSLException


@Mockable
class FileHelper private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<FileHelper, Context>(::FileHelper)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun createFile(fileEntry: FileEntryOperations): Boolean {
        createFileDirs(fileEntry)
        return getFile(fileEntry).createNewFile()
    }

    fun createFileDirs(fileEntry: FileEntryOperations): Boolean {
        return getDir(fileEntry).mkdirs()
    }

    fun deleteFile(fileName: String): Boolean {
        return fileEntryRepository.get(fileName)?.let { fileEntry ->
            deleteFile(fileEntry)
        } ?: false
    }

    fun deleteFile(fileEntry: FileEntryOperations): Boolean {
        return getFile(fileEntry).delete()
    }

    fun getDir(fileEntry: FileEntryOperations): File {
        return getFileByPath(fileEntry.folder)
    }

    fun getFile(fileEntryName: String): File? {
        return fileEntryRepository.get(fileEntryName)?.let { getFile(it) }
    }

    fun getFile(fileEntry: FileEntryOperations): File {
        return getFileByPath(fileEntry.path)
    }

    fun writeFile(fileEntry: FileEntryOperations, byteArray: ByteArray) {
        val file = getFile(fileEntry)
        file.writeBytes(byteArray)
    }

    /**
     * writes data from [source] to file of [fileEntry] and return sha265
     * @throws SSLException when connection is terminated while writing to file
     */
    @Throws(SSLException::class)
    fun writeFile(fileEntry: FileEntryOperations, source: BufferedSource): String {
        val fileSink = getFile(fileEntry).sink()
        val hashingSink = HashingSink.sha256(fileSink)

        hashingSink.buffer().apply {
            writeAll(source)
            close()
        }
        fileSink.flush()
        fileSink.close()
        source.close()
        return hashingSink.hash.hex()
    }

    fun getAbsoluteFilePath(fileEntryName: String): String? {
        return fileEntryRepository.get(fileEntryName)?.let { getAbsoluteFilePath(it) }
    }

    fun getAbsoluteFilePath(fileEntry: FileEntryOperations): String {
        return if (!isExternalStorageWritable())
            "file://${applicationContext.filesDir}/${fileEntry.path}"
        else {
            "file://${ContextCompat.getExternalFilesDirs(applicationContext, null)
                .first()}/${fileEntry.path}"
        }
    }

    fun getFileByPath(filePath: String): File {
        return if (!isExternalStorageWritable())
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
                    areEqual = false
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