package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.base.StartupActivity
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.ExternalStorageNotAvailableException
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activty_storage_migration.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class StorageMigrationActivity : StartupActivity() {
    private val log by Log

    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var database: AppDatabase

    private lateinit var storageDataStore: StorageDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activty_storage_migration)

        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        storageDataStore = StorageDataStore.getInstance(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            migrateToSelectableStorage()
            migrateFilesToDesiredStorage()
            withContext(Dispatchers.Main) {
                startActualApp()
                finish()
            }
        }
    }

    /**
     * pre 1.2.0 the app stored files in either internal or external storage preferring external if available.
     * File paths could change if external storage was added or removed leading to inconsistent storage state
     * post 1.2.0 FileEntry has a storageLocation property determining the storage medium. On first start after upgrade
     * we need to determine current file location of each downloaded FileEntry and store this information
     */
    @SuppressLint("SetTextI18n")
    private suspend fun migrateToSelectableStorage() {
        val downloadedButNotStoredFiles = fileEntryRepository.getDownloadedByStorageLocation(
            StorageLocation.NOT_STORED
        )
        log.info("Will search for ${downloadedButNotStoredFiles.size} files and move it to the correct storage")
        if (downloadedButNotStoredFiles.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                description.text = getString(R.string.storage_upgrade_help_text)
                migration_progress.progress = 0
                migration_progress.max = downloadedButNotStoredFiles.size
            }
        }
        downloadedButNotStoredFiles.forEachIndexed { index, fileEntry ->
            // determine the current location if location is NOT_STORED
            if (fileEntry.storageLocation == StorageLocation.NOT_STORED) {
                val newEntry = when {
                    storageService.getFullPath(StorageLocation.INTERNAL, fileEntry.path)
                        ?.let(::File)?.exists() ?: false -> {
                        fileEntryRepository.saveOrReplace(fileEntry.copy(storageLocation = StorageLocation.INTERNAL))
                    }
                    storageService.getFullPath(StorageLocation.EXTERNAL, fileEntry.path)
                        ?.let(::File)?.exists() ?: false -> {
                        fileEntryRepository.saveOrReplace(fileEntry.copy(storageLocation = StorageLocation.EXTERNAL))
                    }
                    else -> {
                        log.warn("${fileEntry.name} has a download date but was not found during migration, resetting download date")
                        fileEntryRepository.resetDownloadDate(fileEntry)
                        null
                    }
                }
                if ((newEntry?.let {
                        storageService.ensureFileIntegrity(
                            it,
                            fileEntry.sha256
                        )
                    }) == false) {
                    log.warn("Corrupt file at ${storageService.getAbsolutePath(fileEntry)}, resetting download date")
                    fileEntryRepository.resetDownloadDate(fileEntry)
                }
            }
            withContext(Dispatchers.Main) {
                migration_progress.progress = index + 1
                numeric_progress.text = "${index + 1} / ${migration_progress.max}"
            }
        }
    }

    /**
     * This function collects all FileEntrys that have a storageLocation different than the currently selected
     * NOT_STORED is excluded obviously. After collecting the files they are copied to the desired [StorageLocation]
     * Should only take any effect if the user changes the file storage location in settings and then restarts the app
     */
    @SuppressLint("SetTextI18n")
    private suspend fun migrateFilesToDesiredStorage() {
        val currentStorageLocation = storageDataStore.storageLocation.get()
        val allFilesNotOnDesiredStorage = fileEntryRepository.getExceptStorageLocation(
            listOf(currentStorageLocation, StorageLocation.NOT_STORED)
        )
        val fileCount = allFilesNotOnDesiredStorage.size
        log.info("Will move $fileCount to the correct storage")

        withContext(Dispatchers.Main) {
            description.text = getString(R.string.storage_migration_help_text)
            migration_progress.progress = 0
            migration_progress.max = fileCount
        }

        allFilesNotOnDesiredStorage.forEachIndexed { index, originalFileEntry ->
            database.runInTransaction<Unit> {
                val movedFileEntry =
                    fileEntryRepository.saveOrReplace(originalFileEntry.copy(storageLocation = currentStorageLocation))
                // If target storage has changed move files accordingly
                val oldFile = try {
                    storageService.getFile(originalFileEntry)
                } catch (e: ExternalStorageNotAvailableException) {
                    fileEntryRepository.resetDownloadDate(movedFileEntry)
                    return@runInTransaction
                }
                val newFile = storageService.getFile(movedFileEntry)
                try {
                    if (oldFile != null && newFile != null) {
                        oldFile.copyTo(newFile, overwrite = true)
                    } else {
                        fileEntryRepository.resetDownloadDate(movedFileEntry)
                        return@runInTransaction
                    }
                } catch (e: Exception) {
                    log.error("Failure trying to move ${oldFile?.absolutePath} to ${newFile?.absolutePath}")
                    fileEntryRepository.resetDownloadDate(movedFileEntry)
                    return@runInTransaction
                }
                try {
                    oldFile.delete()
                } catch (e: Exception) {
                    log.warn("Cleaning up old file ${oldFile.name} failed")
                }

                runBlocking(Dispatchers.Main) {
                    migration_progress.progress = index + 1
                    numeric_progress.text = "${index + 1} / $fileCount"
                }
            }
        }
    }
}