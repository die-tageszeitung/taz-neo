package de.taz.app.android.persistence.migrations

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import androidx.core.database.getFloatOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.api.models.AppName
import de.taz.app.android.api.models.AppType
import de.taz.app.android.api.models.AudioSpeaker
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.allMigrations
import de.taz.app.android.persistence.typeconverters.AudioSpeakerConverter
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@RunWith(AndroidJUnit4::class)
@Suppress("BlockingMethodInNonBlockingContext")
class MigrationTest {
    private val testDb = "migration-test"

    private lateinit var context: Context

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun getMigratedRoomDatabase(): AppDatabase {
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
    fun migrate1To2() = runTest {
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

        val fromDB = getMigratedRoomDatabase().appInfoDao().get()
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
    fun migrate2To3() = runTest {
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

        val fromDB = getMigratedRoomDatabase().sectionDao().get(sectionFileName)
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
    fun migrate3To4() = runTest {
        val feedName = "rss"
        val date = "1869-06-27"
        val key = "key"
        val baseUrl = "https://example.com"
        val status: IssueStatus = IssueStatus.demo
        val minResourceVersion = 23
        val zipName = "zipName"
        val zipPdfName = "zipPdf"
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
            getMigratedRoomDatabase().issueDao().getByFeedDateAndStatus(feedName, date, status)
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
    fun migrate4To5() = runTest {
        val feedName = "rss"
        val date = "1869-06-27"
        val key = "key"
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
            getMigratedRoomDatabase().issueDao().getByFeedDateAndStatus(feedName, date, status)
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
    fun migrate27to28() {
        val values = ContentValues()
        var db = helper.createDatabase(testDb, 27)

        val articleFileName1 = "art03786143.public.html"
        val articleFileName2 = "art03786145.public.html"
        val audioFileName1 = "Media.1254823.public.mp3"
        val unknownAudioSpeaker = AudioSpeakerConverter().toString(AudioSpeaker.UNKNOWN)

        // Prepare the database at version 27 by inserting two articles, of which one has a audio file
        db.apply {
            values.apply {
                clear()
                put("articleFileName", articleFileName1)
                put("issueFeedName", "test")
                put("issueDate", "2023-10-11")
                put("pageNameList", "[]")
                put("articleType", "STANDARD")
                put("position", 0)
                put("percentage", 0)
                put("hasAudio", true)
            }
            insert("Article", CONFLICT_ABORT, values)

            values.apply {
                clear()
                put("articleFileName", articleFileName1)
                put("audioFileName", audioFileName1)
            }
            insert("ArticleAudioFileJoin", CONFLICT_ABORT, values)

            values.apply {
                clear()
                put("articleFileName", articleFileName2)
                put("issueFeedName", "test")
                put("issueDate", "2023-10-11")
                put("pageNameList", "[]")
                put("articleType", "STANDARD")
                put("position", 0)
                put("percentage", 0)
                put("hasAudio", false)
            }
            insert("Article", CONFLICT_ABORT, values)

            close()
        }

        // Migrate the database to version 28
        db = helper.runMigrationsAndValidate(testDb, 28, true, Migration27to28)

        // Verify that the data was migrated correctly
        db.query(
            "SELECT audioFileName FROM Article WHERE articleFileName=?",
            arrayOf(articleFileName1)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(audioFileName1, cursor.getString(0))
        }

        db.query(
            "SELECT playtime, duration, speaker, breaks FROM Audio WHERE fileName=?",
            arrayOf(audioFileName1)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(null, cursor.getIntOrNull(0))
            assertEquals(null, cursor.getFloatOrNull(1))
            assertEquals(unknownAudioSpeaker, cursor.getString(2))
            assertEquals(null, cursor.getStringOrNull(3))
        }

        db.query(
            "SELECT audioFileName FROM Article WHERE articleFileName=?",
            arrayOf(articleFileName2)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(null, cursor.getStringOrNull(0))
        }
    }

}
