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

private const val DATABASE_VERSION = 1
private const val DATABASE_NAME = "db"

@Database(
    entities = [
        AppInfo::class,
        ArticleAudioFileJoin::class,
        ArticleAuthorImageJoin::class,
        ArticleStub::class,
        ArticleImageJoin::class,
        DownloadStub::class,
        Feed::class,
        FileEntry::class,
        IssueStub::class,
        IssueImprintJoin::class,
        IssueMomentJoin::class,
        IssuePageJoin::class,
        IssueSectionJoin::class,
        PageStub::class,
        ResourceInfoStub::class,
        ResourceInfoFileEntryJoin::class,
        SectionStub::class,
        SectionArticleJoin::class,
        SectionImageJoin::class
    ],
    version = DATABASE_VERSION
)
@TypeConverters(
    AppNameTypeConverter::class,
    AppTypeTypeConverter::class,
    ArticleTypeTypeConverter::class,
    CycleTypeConverter::class,
    DownloadStatusTypeConverter::class,
    FrameListTypeConverter::class,
    IssueStatusTypeConverter::class,
    IssueDateDownloadTypeConverter::class,
    NavButtonTypeConverter::class,
    PageTypeTypeConverter::class,
    StorageTypeConverter::class,
    StringListTypeConverter::class,
    SectionTypeTypeConverter::class,
    UUIDTypeConverter::class,
    SectionTypeTypeConverter::class
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
    abstract fun articleAuthorImageJoinDao(): ArticleAuthorImageJoinDao
    abstract fun articleImageJoinDao(): ArticleImageJoinDao
    abstract fun downloadDao(): DownloadDao
    abstract fun feedDao(): FeedDao
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun issueDao(): IssueDao
    abstract fun issueImprintJoinDao(): IssueImprintJoinDao
    abstract fun issueMomentJoinDao(): IssueMomentJoinDao
    abstract fun issuePageJoinDao(): IssuePageJoinDao
    abstract fun issueSectionJoinDao(): IssueSectionJoinDao
    abstract fun pageDao(): PageDao
    abstract fun resourceInfoDao(): ResourceInfoDao
    abstract fun resourceInfoFileEntryJoinDao(): ResourceInfoFileEntryJoinDao
    abstract fun sectionArticleJoinDao(): SectionArticleJoinDao
    abstract fun sectionDao(): SectionDao
    abstract fun sectionImageJoinDao(): SectionImageJoinDao

}