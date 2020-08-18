package de.taz.app.android.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.migrations.*
import de.taz.app.android.persistence.dao.*
import de.taz.app.android.persistence.join.*
import de.taz.app.android.persistence.typeconverters.*
import de.taz.app.android.util.SingletonHolder

const val DATABASE_VERSION = 12
const val DATABASE_NAME = "db"

val allMigrations = arrayOf(
    Migration1to2,
    Migration2to3,
    Migration3to4,
    Migration4to5,
    Migration5to6,
    Migration6to7,
    Migration7to8,
    Migration8to9,
    Migration9to10,
    Migration10to11,
    Migration11to12
)

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
        ImageStub::class,
        IssueStub::class,
        IssueImprintJoin::class,
        IssueCreditMomentJoin::class,
        IssueImageMomentJoin::class,
        IssuePageJoin::class,
        IssueSectionJoin::class,
        MomentStub::class,
        PageStub::class,
        ResourceInfoStub::class,
        ResourceInfoFileEntryJoin::class,
        SectionStub::class,
        SectionArticleJoin::class,
        SectionImageJoin::class,
        SectionNavButtonJoin::class
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
    ImageResolutionTypeConverter::class,
    ImageTypeTypeConverter::class,
    IssueStatusTypeConverter::class,
    IssueDateDownloadTypeConverter::class,
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
            .addMigrations(*allMigrations)
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
    abstract fun imageDao(): ImageDao
    abstract fun imageStubDao(): ImageStubDao
    abstract fun issueDao(): IssueDao
    abstract fun issueImprintJoinDao(): IssueImprintJoinDao
    abstract fun issueCreditMomentJoinDao(): IssueCreditMomentJoinDao
    abstract fun issueImageMomentJoinDao(): IssueImageMomentJoinDao
    abstract fun issuePageJoinDao(): IssuePageJoinDao
    abstract fun issueSectionJoinDao(): IssueSectionJoinDao
    abstract fun momentDao(): MomentDao
    abstract fun pageDao(): PageDao
    abstract fun resourceInfoDao(): ResourceInfoDao
    abstract fun resourceInfoFileEntryJoinDao(): ResourceInfoFileEntryJoinDao
    abstract fun sectionArticleJoinDao(): SectionArticleJoinDao
    abstract fun sectionDao(): SectionDao
    abstract fun sectionImageJoinDao(): SectionImageJoinDao
    abstract fun sectionNavButtonJoinDao(): SectionNavButtonJoinDao

}