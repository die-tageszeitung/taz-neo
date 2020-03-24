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
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.allMigrations
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
        context = ApplicationProvider.getApplicationContext()
    }

    private fun getMigratedRoomDatabase(): AppDatabase? {
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java, testDb
        ).addMigrations(*allMigrations).build()
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

        fromDB?.let {
            Assert.assertEquals(fromDB.appName, appName)
            Assert.assertEquals(fromDB.globalBaseUrl, globalBaseUrl)
            Assert.assertEquals(fromDB.appType, appType)
            Assert.assertEquals(fromDB.androidVersion, 0)
        }
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

        fromDB.let {
            Assert.assertEquals(fromDB.sectionFileName, sectionFileName)
            Assert.assertEquals(fromDB.issueDate, issueDate)
            Assert.assertEquals(fromDB.title, title)
            Assert.assertEquals(fromDB.type, type)
            Assert.assertEquals(fromDB.extendedTitle, null)
        }
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

        helper.runMigrationsAndValidate(testDb, 4, true, Migration3to4)

        val fromDB: IssueStub? =
            getMigratedRoomDatabase()!!.issueDao().getByFeedDateAndStatus(feedName, date, status)
        Assert.assertNotNull(fromDB)

        fromDB?.let {
            Assert.assertEquals(fromDB.feedName, feedName)
            Assert.assertEquals(fromDB.date, date)
            Assert.assertEquals(fromDB.key, key)
            Assert.assertEquals(fromDB.baseUrl, baseUrl)
            Assert.assertEquals(fromDB.status, status)
            Assert.assertEquals(fromDB.minResourceVersion, minResourceVersion)
            Assert.assertEquals(fromDB.dateDownload, dateDownload)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        val feedName = "rss"
        val date = "1869-06-27"
        val key: String? = "key"
        val baseUrl = "https://example.com"
        val status: IssueStatus = IssueStatus.demo
        val minResourceVersion = 23
        val dateDownload: Date? = null
        helper.createDatabase(testDb, 4).apply {
            execSQL(
                """INSERT INTO Issue (feedName, date, key, baseUrl, status, minResourceVersion, dateDownload)
                   VALUES ('$feedName', '$date', '$key', '$baseUrl', '$status',
                    $minResourceVersion, '$dateDownload'
                    )""".trimMargin()
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 5, true, Migration4to5)

        val fromDB: IssueStub? =
            getMigratedRoomDatabase()!!.issueDao().getByFeedDateAndStatus(feedName, date, status)
        Assert.assertNotNull(fromDB)

        fromDB?.let {
            Assert.assertEquals(fromDB.feedName, feedName)
            Assert.assertEquals(fromDB.date, date)
            Assert.assertEquals(fromDB.key, key)
            Assert.assertEquals(fromDB.baseUrl, baseUrl)
            Assert.assertEquals(fromDB.status, status)
            Assert.assertEquals(fromDB.minResourceVersion, minResourceVersion)
            Assert.assertEquals(fromDB.dateDownload, dateDownload)
            Assert.assertFalse(fromDB.isWeekend)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        val fileName = "filenamen.html"
        val baseUrl = "example.com/tp/"
        val status = DownloadStatus.aborted
        val workerManagerId = UUID.randomUUID()

        helper.createDatabase(testDb, 5).apply {
            execSQL(
                """INSERT INTO Download (fileName, baseUrl, status, workerManagerId)
                   VALUES ('$fileName', '$baseUrl', '$status', '$workerManagerId' )""".trimMargin()
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 6, true, Migration5to6)

        val fromDB = getMigratedRoomDatabase()!!.downloadDao().get(fileName)

        Assert.assertNotNull(fromDB)

        fromDB?.let {
            Assert.assertEquals(fromDB.fileName, fileName)
            Assert.assertEquals(fromDB.baseUrl, baseUrl)
            Assert.assertEquals(fromDB.status, status)
            Assert.assertEquals(fromDB.workerManagerId, workerManagerId)
            Assert.assertNull(fromDB.lastSha256)
        }
    }



}
