package de.taz.app.android.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.dao.*
import de.taz.app.android.persistence.join.*
import de.taz.app.android.persistence.typeconverters.*
import de.taz.app.android.util.SingletonHolder

private const val DATABASE_VERSION = 5
private const val DATABASE_NAME = "db"

@Database(
    entities = [
        AppInfo::class,
        ArticleAudioFileJoin::class,
        ArticleBase::class,
        ArticleImageJoin::class,
        FileEntry::class,
        IssueBase::class,
        IssuePageJoin::class,
        PageWithoutFile::class,
        ResourceInfoWithoutFiles::class,
        ResourceInfoFileEntryJoin::class
    ],
    version = DATABASE_VERSION
)
@TypeConverters(
    AppNameTypeConverter::class,
    AppTypeTypeConverter::class,
    FrameListTypeConverter::class,
    IssueStatusTypeConverter::class,
    NavButtonTypeconverter::class,
    PageTypeTypeConverter::class,
    StorageTypeConverter::class,
    StringListTypeConverter::class
)
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
    abstract fun articleDao(): ArticleDao
    abstract fun articleAudioFileJoinDao(): ArticleAudioFileJoinDao
    abstract fun articleImageJoinDao(): ArticleImageJoinDao
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun issueDao(): IssueDao
    abstract fun issuePageJoinDao(): IssuePageJoinDao
    abstract fun pageDao(): PageDao
    abstract fun resourceInfoDao(): ResourceInfoDao
    abstract fun resourceInfoFileEntryJoinDao(): ResourceInfoFileEntryJoinDao
}