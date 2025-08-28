package de.taz.app.android.persistence.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.DATABASE_VERSION
import de.taz.app.android.persistence.allMigrations
import de.taz.test.RobolectricTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class AllMigrationsTest {
    private val testDb = "migration-test"

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
        helper.runMigrationsAndValidate(testDb, DATABASE_VERSION, true, *allMigrations())
    }
}
