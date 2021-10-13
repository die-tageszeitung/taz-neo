package de.taz.app.android.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import de.taz.app.android.api.ApiService
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.StorageService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class DownloadServiceTest {

    private lateinit var downloadService: DownloadService


    private val mockHttpClient: HttpClient = HttpClient(MockEngine) {
        engine { addHandler { respond("") } }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        downloadService = DownloadService(
            context,
            FileEntryRepository.getInstance(context),
            IssueRepository.getInstance(context),
            ApiService.getInstance(context),
            StorageService.getInstance(context),
            mockHttpClient,
            DownloadDataStore.getInstance(context)
        )
    }

    @Test
    fun pollNewIssue() = runBlocking {
        val operation = downloadService.scheduleNewestIssueDownload("test", true)
        Assert.assertNotNull(operation)
    }
}