package de.taz.app.android.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.viewbinding.ViewBinding
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.content.ContentService
import de.taz.app.android.base.StartupActivity
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.databinding.ActivityStorageMigrationBinding
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ExternalStorageNotAvailableException
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class StorageOrganizationActivity : StartupActivity() {
    private val log by Log

    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var database: AppDatabase

    private lateinit var storageDataStore: StorageDataStore
    private lateinit var issueRepository: IssueRepository
    private lateinit var contentService: ContentService
    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper

    private lateinit var viewBinding: ActivityStorageMigrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityStorageMigrationBinding.inflate(layoutInflater)

        fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        storageDataStore = StorageDataStore.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)
        authHelper = AuthHelper.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        lifecycleScope.launch {
            migrateToSelectableStorage()
            migrateFilesToDesiredStorage()

            if (authHelper.getMinStatus() == IssueStatus.regular) {
                deletePublicIssues()
            }

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
                viewBinding.description.text = getString(R.string.storage_upgrade_help_text)
                viewBinding.migrationProgress.progress = 0
                viewBinding.migrationProgress.max = downloadedButNotStoredFiles.size
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
                viewBinding.migrationProgress.progress = index + 1
                viewBinding.numericProgress.text = "${index + 1} / ${viewBinding.migrationProgress.max}"
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
            viewBinding.description.text = getString(R.string.storage_migration_help_text)
            viewBinding.migrationProgress.progress = 0
            viewBinding.migrationProgress.max = fileCount
        }

        allFilesNotOnDesiredStorage.forEachIndexed { index, originalFileEntry ->
            database.withTransaction {
                val movedFileEntry =
                    fileEntryRepository.saveOrReplace(originalFileEntry.copy(storageLocation = currentStorageLocation))
                // If target storage has changed move files accordingly
                val oldFile = try {
                    storageService.getFile(originalFileEntry)
                } catch (e: ExternalStorageNotAvailableException) {
                    fileEntryRepository.resetDownloadDate(movedFileEntry)
                    return@withTransaction
                }
                val newFile = storageService.getFile(movedFileEntry)
                try {
                    if (oldFile != null && newFile != null) {
                        oldFile.copyTo(newFile, overwrite = true)
                    } else {
                        fileEntryRepository.resetDownloadDate(movedFileEntry)
                        return@withTransaction
                    }
                } catch (e: Exception) {
                    log.error("Failure trying to move ${oldFile?.absolutePath} to ${newFile?.absolutePath}")
                    fileEntryRepository.resetDownloadDate(movedFileEntry)
                    return@withTransaction
                }
                try {
                    oldFile.delete()
                } catch (e: Exception) {
                    log.warn("Cleaning up old file ${oldFile.name} failed")
                }

                runBlocking(Dispatchers.Main) {
                    viewBinding.migrationProgress.progress = index + 1
                    viewBinding.numericProgress.text = "${index + 1} / $fileCount"
                }
            }
        }
    }

    /**
     * This function will prune all data associated to public / demo issues
     */
    private suspend fun deletePublicIssues() {
        val issueStubsToDelete = issueRepository.getAllPublicAndDemoIssueStubs()
        val issueCount = issueStubsToDelete.size
        log.info("Will delete $issueCount public issues")

        withContext(Dispatchers.Main) {
            viewBinding.description.text = getString(R.string.storage_migration_help_text_delete_issues)
            viewBinding.migrationProgress.progress = 0
            viewBinding.migrationProgress.max = issueCount
        }
        var count = 0
        var errors = 0
        for (issueStub in issueStubsToDelete) {
            try {
                contentService.deleteIssue(issueStub.issueKey)
            } catch (e: CacheOperationFailedException) {
                val hint = "Issue deleting public issues during startup storage organization"
                log.error(hint)
                Sentry.captureException(e, hint)
                errors++
            }
            withContext(Dispatchers.Main) {
                count++
                viewBinding.migrationProgress.progress = count
                viewBinding.numericProgress.text = "$count / $issueCount"
            }
        }
        if (errors > 0) {
            toastHelper.showToast(R.string.storage_migration_help_text_delete_issues_error)
        }
    }
}