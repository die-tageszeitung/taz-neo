package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.models.AppName
import de.taz.app.android.api.models.AppType
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.persistence.AppDatabase
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInfoRepositoryTest {

    private lateinit var appInfoRepository: AppInfoRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appInfoRepository = AppInfoRepository.getInstance(context)
        appInfoRepository.appDatabase = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java).build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        appInfoRepository.appDatabase.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAppInfoAndRead() = runTest {
        appInfoRepository.save(appInfoTazTest)
        val fromDB = appInfoRepository.get()
        assertEquals(fromDB, appInfoTazTest)
    }

    @Test
    @Throws(Exception::class)
    fun writeMultipleGetLatest() = runTest {
        appInfoRepository.save(appInfoTazTest)
        appInfoRepository.save(appInfoTazProduction)

        assertEquals(appInfoRepository.get(), appInfoTazProduction)
    }

    @Test
    @Throws(Exception::class)
    fun ensureOnlyOneEntryInDB() = runTest {
        appInfoRepository.save(appInfoTazTest)
        appInfoRepository.save(appInfoTazProduction)

        assertEquals(appInfoRepository.getCount(), 1)
    }

}

val appInfoTazTest = AppInfo(AppName.taz, "http://example.com/1/", AppType.test, 0)
val appInfoTazProduction = AppInfo(AppName.taz, "http://example.com/2/", AppType.production, 1)