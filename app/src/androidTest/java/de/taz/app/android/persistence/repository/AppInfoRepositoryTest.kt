package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.persistence.AppDatabase
import kotlinx.io.IOException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInfoRepositoryTest {
    private lateinit var db: AppDatabase

    private lateinit var appInfoRepository: AppInfoRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
                context, AppDatabase::class.java).build()
        appInfoRepository = AppInfoRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeAppInfoAndRead() {
        appInfoRepository.save(appInfoTazTest)
        val fromDB = appInfoRepository.get()
        assertEquals(fromDB, appInfoTazTest)
    }

    @Test
    @Throws(Exception::class)
    fun writeMultipleGetLatest() {
        appInfoRepository.save(appInfoTazTest)
        appInfoRepository.save(appInfoTazProduction)

        assertEquals(appInfoRepository.get(), appInfoTazProduction)
    }

    @Test
    @Throws(Exception::class)
    fun ensureOnlyOneEntryInDB() {
        appInfoRepository.save(appInfoTazTest)
        appInfoRepository.save(appInfoTazProduction)

        assertEquals(appInfoRepository.getCount(), 1)
    }

}

val appInfoTazTest = AppInfo(AppName.taz, "http://example.com/1/", AppType.test)
val appInfoTazProduction = AppInfo(AppName.taz, "http://example.com/2/", AppType.production)