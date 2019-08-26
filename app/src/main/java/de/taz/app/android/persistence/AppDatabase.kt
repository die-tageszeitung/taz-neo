package de.taz.app.android.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.dao.*
import de.taz.app.android.persistence.entities.*
import de.taz.app.android.util.SingletonHolder

private const val DATABASE_VERSION = 2
private const val DATABASE_NAME = "db"

@Database(
    entities = [AppInfo::class, FileEntry::class, ResourceInfoEntity::class,
        ResourceInfoFileEntryJoin::class
    ],
    version = DATABASE_VERSION
)
@TypeConverters(AppNameConverter::class, AppTypeConverter::class, StorageTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    companion object : SingletonHolder<AppDatabase, Context>({ applicationContext: Context ->
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, DATABASE_NAME
        )
            // TODO add migrations and TESTS for them
            .fallbackToDestructiveMigration()
            .build()
    })

    abstract fun appInfoDao(): AppInfoDao
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun resourceInfoDao(): ResourceInfoDao
    abstract fun resourceInfoFileEntryJoinDao(): ResourceInfoFileEntryJoinDao
}