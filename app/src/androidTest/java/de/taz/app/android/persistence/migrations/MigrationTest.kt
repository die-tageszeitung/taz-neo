package de.taz.app.android.persistence.migrations

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.api.dto.AppName
import de.taz.app.android.api.dto.AppType
import de.taz.app.android.persistence.AppDatabase
import kotlinx.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.api.models.SectionStub
import org.junit.Assert


@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test"

    private lateinit var context: Context

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    private fun getMigratedRoomDatabase(): AppDatabase? {
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java, testDb
        ).addMigrations(
                Migration1to2,
                Migration2to3
            ).build()
        // close the database and release any stream resources when the test finishes
        helper.closeWhenFinished(database)
        return database
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        val appName = AppName.taz
        val globalBaseUrl = "https://example.com"
        val appType = AppType.test
        helper.createDatabase(testDb, 1).apply {
            // db has schema version 1. insert some data using SQL queries.
            // You cannot use DAO classes because they expect the latest schema.
            execSQL(
                """INSERT INTO AppInfo (appName, globalBaseUrl, appType)
                   VALUES ('$appName', '$globalBaseUrl', '$appType')""".trimMargin()
            )
            // Prepare for the next version.
            close()
        }

        helper.runMigrationsAndValidate(testDb, 2, true, Migration1to2)

        val fromDB = getMigratedRoomDatabase()!!.appInfoDao().get()
        Assert.assertNotNull(fromDB)
        Assert.assertEquals(fromDB, AppInfo(appName, globalBaseUrl, appType, 0))

    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val sectionFileName = "sectionFileName"
        val issueDate = "1886-05-01"
        val title = "section title"
        val type = SectionType.articles
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                """INSERT INTO Section (sectionFileName, issueDate, title, type)
                   VALUES ('$sectionFileName', '$issueDate', '$title', '$type')""".trimMargin()
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 3, true, Migration2to3)

        val fromDB = getMigratedRoomDatabase()!!.sectionDao().get(sectionFileName)
        Assert.assertNotNull(fromDB)
        Assert.assertEquals(fromDB, SectionStub(sectionFileName, issueDate, title, type, null))
    }
}
