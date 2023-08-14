package de.taz.app.android.singletons

import android.content.Context
import android.os.Environment
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.GLOBAL_FOLDER
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.SingletonHolder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

const val COPY_BUFFER_SIZE = 100 * 1024 // 100kiB

interface Storable {
    val name: String
    val storageType: StorageType
}

class ExternalStorageNotAvailableException(message: String) : Exception(message)


class StorageService private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<StorageService, Context>(::StorageService) {
        fun determineFilePath(storageType: StorageType, name: String, issueKey: IssueKey?): String {
            val folder = when (storageType) {
                StorageType.global -> GLOBAL_FOLDER
                StorageType.resource -> RESOURCE_FOLDER
                StorageType.issue -> {
                    if (issueKey == null) {
                        throw IllegalStateException("Determining the file path of an issue file requires issueKey to be non-null")
                    }
                    "${issueKey.feedName}/${issueKey.date}/${issueKey.status}"
                }
            }
            return "${folder}/${name}"
        }

        fun determineFilePath(storable: Storable, issueKey: IssueKey?): String {
            return determineFilePath(storable.storageType, storable.name, issueKey)
        }
    }

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)

    private val filesDir by lazy { applicationContext.filesDir }
    fun getInternalFilesDir(): File = filesDir

    fun getExternalFilesDir(): File? {
        return applicationContext.getExternalFilesDir(null)
    }

    /**
     * Get the directory associated to a [StorageLocation]
     * @param storageLocation Member of [StorageLocation] enum
     * @return A file object referencing the destination of the [storageLocation], might be null in case of [StorageLocation.NOT_STORED]
     */
    fun getDirForLocation(storageLocation: StorageLocation): File? {
        return when (storageLocation) {
            StorageLocation.INTERNAL -> getInternalFilesDir()
            StorageLocation.EXTERNAL -> {
                try {
                    getExternalFilesDir()!!
                } catch (e: NullPointerException) {
                    throw ExternalStorageNotAvailableException("getExternalFilesDir() returned null")
                }
            }
            StorageLocation.NOT_STORED -> null
        }
    }

    fun getFullPath(storageLocation: StorageLocation, path: String): String? {
        return when (storageLocation) {
            StorageLocation.INTERNAL -> "${getInternalFilesDir().absolutePath}/${path}"
            StorageLocation.EXTERNAL -> getExternalFilesDir()?.let { "${getExternalFilesDir()?.absolutePath}/${path}" }
            else -> null
        }
    }

    fun externalStorageAvailable(): Boolean {
        val externalStorageAvailable =
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        val externalStorageEmulated = Environment.isExternalStorageEmulated()
        val externalStorageRemovable = Environment.isExternalStorageRemovable()
        return externalStorageAvailable && (!externalStorageEmulated || externalStorageRemovable)
    }

    /**
     * Get the absolute path to a file referenced by this file entry
     * @param fileEntry A FileEntry instance
     * @return An absolute path to the file, might be null if the referenced file is not stored on the local file system
     */
    fun getAbsolutePath(fileEntry: FileEntryOperations): String? {
        return getDirForLocation(fileEntry.storageLocation)?.let { "$it/${fileEntry.path}" }
    }

    /**
     * Build an absolute path specifying a relative path and its [StorageLocation]
     * @param path A relative path
     * @param storageLocation A storage location - [StorageLocation.NOT_STORED] is illegal though and will throw an [IllegalStateException]
     * @return Absolute path to [path]
     */
    fun getAbsolutePath(path: String, storageLocation: StorageLocation): String? {
        return getDirForLocation(storageLocation)?.let { "${it.absolutePath}/${path}" }
    }

    /**
     * Get the absolute path to the referenced [fileEntry] in URI format ("file://[..]")
     * @param fileEntry A [FileEntryOperations] instance
     * @return An absolute path to the file, might be null if the referenced file is not stored on the local file system
     */
    fun getFileUri(fileEntry: FileEntryOperations): String? {
        return getAbsolutePath(fileEntry)?.let { "file://$it" }
    }


    fun getFile(fileEntry: FileEntryOperations): File? {
        return getAbsolutePath(fileEntry)?.let(::File)
    }

    /**
     * writes data from [channel] to file of [fileEntry] and return sha256
     */
    suspend fun writeFile(fileEntry: FileEntryOperations, channel: ByteReadChannel): String {
        if (fileEntry.storageLocation == StorageLocation.NOT_STORED) {
            throw IllegalStateException("Cannot write file to FileEntry with StorageLocation.NOT_STORED")
        }
        val filePath = getAbsolutePath(fileEntry)!!
        val file = File(filePath)
        file.parentFile?.mkdirs()
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

    suspend fun deleteFile(fileEntry: FileEntry) {
        fileEntryRepository.resetDownloadDate(fileEntry)
        getAbsolutePath(fileEntry)?.let {
            File(it).delete()
        }
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
        val assetInputStream = applicationContext.assets.open(path)
        file.parentFile?.mkdirs()
        val fileOutputStream = file.outputStream()
        try {
            assetInputStream.copyTo(fileOutputStream)
        } finally {
            assetInputStream.close()
            fileOutputStream.close()
        }
    }

    suspend fun getSHA256(file: File): String = withContext(Dispatchers.Default) {
        val bytes = file.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return@withContext digest.fold("", { str, it -> str + "%02x".format(it) })
    }

    suspend fun ensureFileIntegrity(fileEntry: FileEntry, checksum: String? = null): Boolean =
        withContext(
            Dispatchers.IO
        ) {
            val file = getAbsolutePath(fileEntry)?.let(::File)
            // If we don't provide a checksum we assume the mere existence means it's ok
            return@withContext if (file?.exists() == true) {
                try {
                    checksum?.let { it == getSHA256(file) } ?: true
                } catch (e: FileNotFoundException) {
                    log.warn("File not found during integrity check", e)
                    Sentry.captureException(e)
                    false
                }
            } else {
                false
            }
        }

    suspend fun ensureFileExists(fileEntry: FileEntry): Boolean = withContext(Dispatchers.IO) {
        val file = getAbsolutePath(fileEntry)?.let { File(it) }
        return@withContext file?.exists() ?: false
    }

    suspend fun getNonExistentFilesFromList(files: List<FileEntry>): List<FileEntry> =
        files.filter { !ensureFileExists(it) }

    suspend fun getCorruptedFilesFromList(files: List<FileEntry>): List<FileEntry> =
        files.filter { !ensureFileIntegrity(it, it.sha256) }

    suspend fun ensureFileListExists(files: List<FileEntry>): Boolean {
        return getNonExistentFilesFromList(files).isEmpty()
    }

    suspend fun ensureFileListIntegrity(files: List<FileEntry>): Boolean {
        return getCorruptedFilesFromList(files).isEmpty()
    }

    /**
     * All issue files of given [feedName] will be deleted except for those issues which are hold.
     * @param feedName - [String] representing a feed (ie "taz")
     */
    suspend fun deleteAllUnusedIssueFolders(feedName: String) {
        val holdIssues = issueRepository.getAllIssueStubs()
        val allDirs = getAllIssueDirectories(feedName)
        val abandonedDirectoriesTobeDeleted = filterOutHoldIssues(holdIssues, allDirs)

        abandonedDirectoriesTobeDeleted.forEach {
            log.debug("deleting recursively: ${it.absolutePathString()}")
            File(it.absolutePathString()).deleteRecursively()
        }

    }

    /**
     * The issues are represented in our file system like
     * .../files/[feedName]/[IssueKey.date]
     * This function returns a list of all those [Path]s existing in file system.
     * @param feedName - [String] representing a feed (ie "taz")
     * @return a [List] of [Path]s of directories below .../files/[feedName]/
     */
    private suspend fun getAllIssueDirectories(feedName: String): List<Path> {
        return withContext(Dispatchers.IO) {
            val storageLocation =
                StorageDataStore.getInstance(applicationContext).storageLocation.get()
            val path = getFullPath(storageLocation, "$feedName/")
            path?.let { Path(it).listDirectoryEntries() } ?: emptyList()
        }
    }

    /**
     * This function returns a list of file system paths
     * which are not connected to the given issues.
     * @param issueList - [List] holding [IssueStub]s
     * @param pathList - [List] holding [Path]s to file system issue representations
     * @return a [List] of [Path]s to directories of issues which are not part of the given [issueList]
     */
    private fun filterOutHoldIssues(issueList: List<IssueStub>, pathList: List<Path>): List<Path> {
        return pathList.filterNot {
            it.name in issueList.map { issue -> issue.date }
        }
    }
}