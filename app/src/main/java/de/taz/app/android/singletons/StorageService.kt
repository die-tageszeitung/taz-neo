package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.PUBLIC_FOLDER
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.dto.FileEntryDto
import de.taz.app.android.api.dto.ImageDto
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.interfaces.FileEntryOperations
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.GLOBAL_FOLDER
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.util.SingletonHolder
import io.ktor.utils.io.*
import io.sentry.core.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest

const val COPY_BUFFER_SIZE = 100 * 1024 // 100kiB

interface Storable {
    val name: String
    val storageType: StorageType
}

class ExternalStorageNotAvailableException(message: String): Exception(message)

@Mockable
class StorageService private constructor(private val applicationContext: Context) {

    companion object : SingletonHolder<StorageService, Context>(::StorageService) {
        fun determineFilePath(storable: Storable, issueKey: IssueKey?): String {
            val folder = when (storable.storageType) {
                StorageType.global -> GLOBAL_FOLDER
                StorageType.resource -> RESOURCE_FOLDER
                StorageType.public -> PUBLIC_FOLDER
                StorageType.issue -> {
                    if (issueKey == null) {
                        throw IllegalStateException("Detemining the file path of an issue file requires issueKey to be non-null")
                    }
                    "${issueKey.feedName}/${issueKey.date}/${issueKey.status}"
                }
            }
            return "${folder}/${storable.name}"
        }
    }

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)

    fun getInternalFilesDir(): File {
        return applicationContext.filesDir
    }

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

    fun createOrUpdateFileEntry(fileEntryDto: FileEntryDto, issueKey: IssueKey?): FileEntry {
        val existing = fileEntryRepository.get(fileEntryDto.name)
        val fileEntry = existing?.copy(path = determineFilePath(fileEntryDto, issueKey))
            ?: FileEntry(fileEntryDto, determineFilePath(fileEntryDto, issueKey))
        fileEntryRepository.saveOrReplace(fileEntry)
        return fileEntry
    }

    fun createOrUpdateImage(imageDto: ImageDto, issueKey: IssueKey?): Image {
        val existing = imageRepository.get(imageDto.name)
        val image = existing?.copy(path = determineFilePath(existing, issueKey))
            ?: Image(imageDto, determineFilePath(imageDto, issueKey))
        imageRepository.save(image)
        return image
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

    fun deleteFile(fileEntry: FileEntry) {
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
        val tazApiAssetReader = assetFileReader(path)
        val fileWriter = file.writer()
        tazApiAssetReader.copyTo(fileWriter)
        tazApiAssetReader.close()
        fileWriter.close()
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
                    val hint = "File not found during integrity check"
                    log.error(hint)
                    Sentry.captureException(e, hint)
                    false
                }
            } else {
                false
            }
        }

    suspend fun ensureFileExists(fileEntry: FileEntry): Boolean = withContext(Dispatchers.IO) {
        val file = File(getAbsolutePath(fileEntry))
        return@withContext file.exists()
    }

    suspend fun getNonExistentFilesFromList(files: List<FileEntry>): List<FileEntry> =
        withContext(Dispatchers.IO) {
            return@withContext files.filter { !ensureFileExists(it) }
        }

    suspend fun getCorruptedFilesFromList(files: List<FileEntry>): List<FileEntry> =
        withContext(Dispatchers.IO) {
            return@withContext files.filter { !ensureFileIntegrity(it, it.sha256) }
        }

    suspend fun ensureFileListExists(files: List<FileEntry>): Boolean {
        return getNonExistentFilesFromList(files).isEmpty()
    }

    suspend fun ensureFileListIntegrity(files: List<FileEntry>): Boolean {
        return getCorruptedFilesFromList(files).isEmpty()
    }
}