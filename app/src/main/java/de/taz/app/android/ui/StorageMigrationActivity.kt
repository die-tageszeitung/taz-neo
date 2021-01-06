package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.ExternalStorageNotAvailableException
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.settings.SettingsViewModel
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activty_storage_migration.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StorageMigrationActivity : NightModeActivity(R.layout.activty_storage_migration) {
    private val log by Log

    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            migrateToSelectableStorage()
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
        val currentStorageLocation = settingsViewModel.storageLocationLiveData.value
        val downloadedButNotStoredFiles = fileEntryRepository.getDownloadedByStorageLocation(
            StorageLocation.NOT_STORED
        )
        downloadedButNotStoredFiles.forEach { fileEntry ->
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
                    else -> null
                }
                if ((newEntry?.let {
                        storageService.ensureFileIntegrity(
                            it,
                            fileEntry.sha256
                        )
                    }) == false) {
                    log.warn("Corrupt file at ${storageService.getAbsolutePath(fileEntry)}}")
                    fileEntryRepository.resetDownloadDate(fileEntry)
                }
            }
        }

        val allFilesNotOnDesiredStorage = fileEntryRepository.getExceptStorageLocation(
            listOf(currentStorageLocation, StorageLocation.NOT_STORED)
        )
        val fileCount = allFilesNotOnDesiredStorage.size
        migration_progress.max = fileCount

        allFilesNotOnDesiredStorage.map { fileEntry ->
            val movedFileEntry = fileEntryRepository.saveOrReplace(fileEntry.copy(storageLocation = currentStorageLocation))
            fileEntry to movedFileEntry
        }.forEachIndexed { index, (oldFileEntry, newFileEntry) ->

            // If target storage has changed move files accordingly
            val oldFile = try {
                storageService.getFile(oldFileEntry)
            } catch (e: ExternalStorageNotAvailableException) {
                fileEntryRepository.resetDownloadDate(newFileEntry)
                return@forEachIndexed
            }
            val newFile = storageService.getFile(newFileEntry)
            try {
                if (oldFile != null && newFile != null) {
                    oldFile.copyTo(newFile, overwrite = true)
                } else {
                    fileEntryRepository.resetDownloadDate(newFileEntry)
                    return@forEachIndexed
                }
            } catch (e: Exception) {
                log.error("Failiure trying to move ${oldFile?.absolutePath} to ${newFile?.absolutePath}")
                fileEntryRepository.resetDownloadDate(newFileEntry)
                return@forEachIndexed
            }
            try {
                oldFile.delete()
            } catch (e: Exception) {
                log.warn("Cleaning up old file ${oldFile.name} failed")
            }

            withContext(Dispatchers.Main) {
                migration_progress.progress = index + 1
                numericProgress.text = "${index + 1} / $fileCount"
            }
        }
        startActualApp()
        finish()
    }
}