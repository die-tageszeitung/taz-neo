package de.taz.app.android.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.taz.app.android.api.models.AppInfo
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AudioPlayerItemStub
import de.taz.app.android.api.models.AudioStub
import de.taz.app.android.api.models.BookmarkSynchronization
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.MomentStub
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.api.models.ResourceInfoStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.ViewerState
import de.taz.app.android.persistence.dao.AppInfoDao
import de.taz.app.android.persistence.dao.ArticleAuthorImageJoinDao
import de.taz.app.android.persistence.dao.ArticleDao
import de.taz.app.android.persistence.dao.ArticleImageJoinDao
import de.taz.app.android.persistence.dao.AudioDao
import de.taz.app.android.persistence.dao.AudioPlayerItemsDao
import de.taz.app.android.persistence.dao.BookmarkSynchronizationDao
import de.taz.app.android.persistence.dao.FeedDao
import de.taz.app.android.persistence.dao.FileEntryDao
import de.taz.app.android.persistence.dao.ImageDao
import de.taz.app.android.persistence.dao.ImageStubDao
import de.taz.app.android.persistence.dao.IssueDao
import de.taz.app.android.persistence.dao.IssueImprintJoinDao
import de.taz.app.android.persistence.dao.IssuePageJoinDao
import de.taz.app.android.persistence.dao.IssueSectionJoinDao
import de.taz.app.android.persistence.dao.MomentCreditJoinDao
import de.taz.app.android.persistence.dao.MomentDao
import de.taz.app.android.persistence.dao.MomentFilesJoinDao
import de.taz.app.android.persistence.dao.MomentImageJoinDao
import de.taz.app.android.persistence.dao.PageDao
import de.taz.app.android.persistence.dao.ResourceInfoDao
import de.taz.app.android.persistence.dao.ResourceInfoFileEntryJoinDao
import de.taz.app.android.persistence.dao.SectionArticleJoinDao
import de.taz.app.android.persistence.dao.SectionDao
import de.taz.app.android.persistence.dao.SectionImageJoinDao
import de.taz.app.android.persistence.dao.ViewerStateDao
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.persistence.join.MomentCreditJoin
import de.taz.app.android.persistence.join.MomentFilesJoin
import de.taz.app.android.persistence.join.MomentImageJoin
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.join.SectionImageJoin
import de.taz.app.android.persistence.migrations.Migration10to11
import de.taz.app.android.persistence.migrations.Migration11to12
import de.taz.app.android.persistence.migrations.Migration12to13
import de.taz.app.android.persistence.migrations.Migration13to14
import de.taz.app.android.persistence.migrations.Migration14to15
import de.taz.app.android.persistence.migrations.Migration15to16
import de.taz.app.android.persistence.migrations.Migration16to17
import de.taz.app.android.persistence.migrations.Migration17to18
import de.taz.app.android.persistence.migrations.Migration18to19
import de.taz.app.android.persistence.migrations.Migration19to20
import de.taz.app.android.persistence.migrations.Migration1to2
import de.taz.app.android.persistence.migrations.Migration20to21
import de.taz.app.android.persistence.migrations.Migration21to22
import de.taz.app.android.persistence.migrations.Migration22to23
import de.taz.app.android.persistence.migrations.Migration23to24
import de.taz.app.android.persistence.migrations.Migration24to25
import de.taz.app.android.persistence.migrations.Migration25to26
import de.taz.app.android.persistence.migrations.Migration26to27
import de.taz.app.android.persistence.migrations.Migration27to28
import de.taz.app.android.persistence.migrations.Migration28to29
import de.taz.app.android.persistence.migrations.Migration29to30
import de.taz.app.android.persistence.migrations.Migration2to3
import de.taz.app.android.persistence.migrations.Migration30to31
import de.taz.app.android.persistence.migrations.Migration31to32
import de.taz.app.android.persistence.migrations.Migration32to33
import de.taz.app.android.persistence.migrations.Migration33to34
import de.taz.app.android.persistence.migrations.Migration34to35
import de.taz.app.android.persistence.migrations.Migration35to36
import de.taz.app.android.persistence.migrations.Migration36to37
import de.taz.app.android.persistence.migrations.Migration3to4
import de.taz.app.android.persistence.migrations.Migration4to5
import de.taz.app.android.persistence.migrations.Migration5to6
import de.taz.app.android.persistence.migrations.Migration6to7
import de.taz.app.android.persistence.migrations.Migration7to8
import de.taz.app.android.persistence.migrations.Migration8to9
import de.taz.app.android.persistence.migrations.Migration9to10
import de.taz.app.android.persistence.typeconverters.AppNameTypeConverter
import de.taz.app.android.persistence.typeconverters.AppTypeTypeConverter
import de.taz.app.android.persistence.typeconverters.ArticleTypeTypeConverter
import de.taz.app.android.persistence.typeconverters.AudioSpeakerConverter
import de.taz.app.android.persistence.typeconverters.CycleTypeConverter
import de.taz.app.android.persistence.typeconverters.DownloadStatusTypeConverter
import de.taz.app.android.persistence.typeconverters.FloatListConverter
import de.taz.app.android.persistence.typeconverters.FrameListTypeConverter
import de.taz.app.android.persistence.typeconverters.ImageResolutionTypeConverter
import de.taz.app.android.persistence.typeconverters.ImageTypeTypeConverter
import de.taz.app.android.persistence.typeconverters.IssueDateDownloadTypeConverter
import de.taz.app.android.persistence.typeconverters.IssueStatusTypeConverter
import de.taz.app.android.persistence.typeconverters.PageTypeTypeConverter
import de.taz.app.android.persistence.typeconverters.PublicationDateListTypeConverter
import de.taz.app.android.persistence.typeconverters.SectionTypeTypeConverter
import de.taz.app.android.persistence.typeconverters.StorageLocationConverter
import de.taz.app.android.persistence.typeconverters.StorageTypeConverter
import de.taz.app.android.persistence.typeconverters.StringListTypeConverter
import de.taz.app.android.util.SingletonHolder

const val DATABASE_VERSION = 37
const val DATABASE_NAME = "db"

fun allMigrations() = arrayOf(
    Migration1to2(),
    Migration2to3(),
    Migration3to4(),
    Migration4to5(),
    Migration5to6(),
    Migration6to7(),
    Migration7to8(),
    Migration8to9(),
    Migration9to10(),
    Migration10to11(),
    Migration11to12(),
    Migration12to13(),
    Migration13to14(),
    Migration14to15(),
    Migration15to16(),
    Migration16to17(),
    Migration17to18(),
    Migration18to19(),
    Migration19to20(),
    Migration20to21(),
    Migration21to22(),
    Migration22to23(),
    Migration23to24(),
    Migration24to25(),
    Migration25to26(),
    Migration26to27(),
    Migration27to28(),
    Migration28to29(),
    Migration29to30(),
    Migration30to31(),
    Migration31to32(),
    Migration32to33(),
    Migration33to34(),
    Migration34to35(),
    Migration35to36(),
    Migration36to37(),
)

@Database(
    entities = [
        AppInfo::class,
        ArticleAuthorImageJoin::class,
        ArticleStub::class,
        ArticleImageJoin::class,
        AudioStub::class,
        AudioPlayerItemStub::class,
        BookmarkSynchronization::class,
        Feed::class,
        FileEntry::class,
        ImageStub::class,
        IssueStub::class,
        ViewerState::class,
        IssueImprintJoin::class,
        MomentCreditJoin::class,
        MomentFilesJoin::class,
        MomentImageJoin::class,
        IssuePageJoin::class,
        IssueSectionJoin::class,
        MomentStub::class,
        PageStub::class,
        ResourceInfoStub::class,
        ResourceInfoFileEntryJoin::class,
        SectionStub::class,
        SectionArticleJoin::class,
        SectionImageJoin::class,
    ],
    version = DATABASE_VERSION
)
@TypeConverters(
    AppNameTypeConverter::class,
    AppTypeTypeConverter::class,
    ArticleTypeTypeConverter::class,
    AudioSpeakerConverter::class,
    CycleTypeConverter::class,
    DownloadStatusTypeConverter::class,
    FloatListConverter::class,
    FrameListTypeConverter::class,
    ImageResolutionTypeConverter::class,
    ImageTypeTypeConverter::class,
    IssueDateDownloadTypeConverter::class,
    IssueStatusTypeConverter::class,
    PageTypeTypeConverter::class,
    PublicationDateListTypeConverter::class,
    SectionTypeTypeConverter::class,
    SectionTypeTypeConverter::class,
    StorageLocationConverter::class,
    StorageTypeConverter::class,
    StringListTypeConverter::class,
)
abstract class AppDatabase : RoomDatabase() {
    companion object : SingletonHolder<AppDatabase, Context>({ applicationContext: Context ->
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*allMigrations())
            .fallbackToDestructiveMigration()
            .build()
    })

    abstract fun appInfoDao(): AppInfoDao
    abstract fun articleDao(): ArticleDao
    abstract fun audioDao(): AudioDao
    abstract fun audioPlayerItemsDao(): AudioPlayerItemsDao
    abstract fun articleAuthorImageJoinDao(): ArticleAuthorImageJoinDao
    abstract fun articleImageJoinDao(): ArticleImageJoinDao
    abstract fun bookmarkSynchronizationDao(): BookmarkSynchronizationDao
    abstract fun feedDao(): FeedDao
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun imageDao(): ImageDao
    abstract fun imageStubDao(): ImageStubDao
    abstract fun issueDao(): IssueDao
    abstract fun viewerStateDao(): ViewerStateDao
    abstract fun issueImprintJoinDao(): IssueImprintJoinDao
    abstract fun momentCreditJoinDao(): MomentCreditJoinDao
    abstract fun momentImageJoinJoinDao(): MomentImageJoinDao
    abstract fun momentFilesJoinDao(): MomentFilesJoinDao
    abstract fun issuePageJoinDao(): IssuePageJoinDao
    abstract fun issueSectionJoinDao(): IssueSectionJoinDao
    abstract fun momentDao(): MomentDao
    abstract fun pageDao(): PageDao
    abstract fun resourceInfoDao(): ResourceInfoDao
    abstract fun resourceInfoFileEntryJoinDao(): ResourceInfoFileEntryJoinDao
    abstract fun sectionArticleJoinDao(): SectionArticleJoinDao
    abstract fun sectionDao(): SectionDao
    abstract fun sectionImageJoinDao(): SectionImageJoinDao
}