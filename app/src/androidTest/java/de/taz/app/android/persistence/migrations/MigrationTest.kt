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
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import org.junit.Assert
import java.util.*


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
            Migration2to3,
            Migration3to4
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

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        val feedName = "rss"
        val date = "1869-06-27"
        val key: String? = "key"
        val baseUrl = "https://example.com"
        val status: IssueStatus = IssueStatus.demo
        val minResourceVersion = 23
        val zipName: String? = "zipName"
        val zipPdfName: String? = "zipPdf"
        val fileList: List<String> = emptyList()
        val fileListPdf: List<String> = emptyList()
        val dateDownload: Date? = null
        val navButton = "nabutton dummy"
        helper.createDatabase(testDb, 3).apply {
            execSQL(
                """INSERT INTO Issue (feedName, date, key, baseUrl, status, minResourceVersion, navButton, zipName, zipPdfName, fileList, fileListPdf, dateDownload)
                   VALUES ('$feedName', '$date', '$key', '$baseUrl', '$status',
                    $minResourceVersion, '$navButton', '$zipName', '$zipPdfName', '$fileList',
                     '$fileListPdf', '$dateDownload'
                    )""".trimMargin()
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 3, true, Migration3to4)

        val fromDB: IssueStub? =
            getMigratedRoomDatabase()!!.issueDao().getByFeedDateAndStatus(feedName, date, status)
        Assert.assertNotNull(fromDB)

        val issueStub = IssueStub(
            feedName,
            date,
            key,
            baseUrl,
            status,
            minResourceVersion,
            dateDownload
        )
        Assert.assertEquals(fromDB, issueStub)
    }
}
