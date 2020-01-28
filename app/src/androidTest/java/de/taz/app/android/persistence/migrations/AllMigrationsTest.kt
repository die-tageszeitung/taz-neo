package de.taz.app.android.persistence.migrations

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.persistence.AppDatabase
import kotlinx.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllMigrationsTest {
    private val testDb = "migration-test"

    // Array of all migrations
    private val allMigrations = arrayOf(
        Migration1to2
    )

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database.
        helper.createDatabase(testDb, 1).apply {
            close()
        }

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java,
                testDb
        ).addMigrations(*allMigrations).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
