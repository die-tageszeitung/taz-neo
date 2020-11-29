package de.taz.app.android.singletons

import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.util.SingletonHolder
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest

const val COPY_BUFFER_SIZE = 100 * 1024 // 100kiB

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

    private fun getDir(fileEntry: FileEntryOperations): File {
        var extraFolder = ""
        if (fileEntry.name.contains("/")) {
            extraFolder = "/${fileEntry.name.substringBeforeLast("/")}"
        }
        return getFileByPath(fileEntry.folder + extraFolder)
    }

    fun getFile(fileEntryName: String): File? {
        return fileEntryRepository.get(fileEntryName)?.let { getFile(it) }
    }

    fun getFile(fileEntry: FileEntryOperations): File {
        return getFileByPath(fileEntry.path)
    }

    fun getFileOrCreate(fileEntry: FileEntryOperations): File {
        return try {
            getFileByPath(fileEntry.path)
        } catch (e: FileNotFoundException) {
            createFile(fileEntry)
            getFileByPath(fileEntry.path)
        }
    }

    /**
     * writes data from [channel] to file of [fileEntry] and return sha256
     */
    suspend fun writeFile(fileEntry: FileEntryOperations, channel: ByteReadChannel): String {
        val file = getFileOrCreate(fileEntry)
        // clear out file
        file.writeBytes(ByteArray(0))
        val fileStream = file.outputStream()
        val hash = MessageDigest.getInstance("SHA-256")

        fileStream.use {
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            do {
                val read = channel.readAvailable(buffer)
                if (read > 0) {
                    hash.update(buffer, 0, read)
                    it.write(buffer, 0, read)
                }
            } while (read > 0)
        }

        val digest = hash.digest()
        return digest.fold("", { str, it -> str + "%02x".format(it) })

    }

    fun getAbsoluteFilePath(fileEntryName: String): String? {
        return fileEntryRepository.get(fileEntryName)?.let { getAbsoluteFilePath(it) }
    }

    fun getAbsoluteFilePath(fileEntry: FileEntryOperations): String {
        return if (!isExternalStorageWritable())
            "file://${applicationContext.filesDir}/${fileEntry.path}"
        else {
            val path = ContextCompat.getExternalFilesDirs(applicationContext, null).first()
            "file://${path}/${fileEntry.path}"
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

    private suspend fun getSHA256(file: File): String = withContext(Dispatchers.Default) {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return@withContext digest.fold("", { str, it -> str + "%02x".format(it) })
    }

    suspend fun ensureFileIntegrity(filePath: String, checksum: String? = null): Boolean =
        withContext(
            Dispatchers.IO
        ) {
            val file = getFileByPath(filePath)
            // If we don't provide a checksum we assume the mere existence means it's ok
            return@withContext if (file.exists()) {
                checksum?.let { it == getSHA256(file) } ?: true
            } else {
                false
            }
        }

    suspend fun ensureFileExists(fileEntry: FileEntry): Boolean = withContext(Dispatchers.IO) {
        val file = getFileByPath(fileEntry.path)
        return@withContext file.exists()
    }

    suspend fun getNonExistentFilesFromList(files: List<FileEntry>): List<FileEntry> =
        withContext(Dispatchers.IO) {
            return@withContext files.filter { !ensureFileExists(it) }
        }

    suspend fun getCorruptedFilesFromList(files: List<FileEntry>): List<FileEntry> =
        withContext(Dispatchers.IO) {
            return@withContext files.filter { !ensureFileIntegrity(it.path, it.sha256) }
        }

    suspend fun ensureFileListExists(files: List<FileEntry>): Boolean {
        return getNonExistentFilesFromList(files).isEmpty()
    }

    suspend fun ensureFileListIntegrity(files: List<FileEntry>): Boolean {
        return getCorruptedFilesFromList(files).isEmpty()
    }
}