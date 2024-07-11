package de.taz.app.android.scrubber

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.taz.app.android.KEEP_LATEST_MOMENTS_COUNT
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.AudioStub
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.MomentStub
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageStub
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.ResourceInfoStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.AudioRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.share.ShareArticleDownloadHelper
import de.taz.app.android.util.Log
import java.io.IOException

/**
 * The Scrubber must not be called when the App is active, as we assume no concurrent database changes.
 * The general idea is to find orphaned database entries and delete them recursively.
 * The Scrubber heavily relies on correct foreign key definitions for the database, to prevent
 * entities still being referenced from being deleted - especially when deleting FileEntry data.
 */
class Scrubber(applicationContext: Context) {

    private val log by Log

    private val appDatabase = AppDatabase.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val audioRepository = AudioRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val resourceInfoRepository = ResourceInfoRepository.getInstance(applicationContext)
    private val shareArticleDownloadHelper = ShareArticleDownloadHelper(applicationContext)

    private val defaultNavDrawerFileName =
        applicationContext.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)

    /**
     * Start a minimal scrub run.
     * Might run in the background on app start.
     */
    suspend fun scrubMinimal() {
        shareArticleDownloadHelper.cleanup()
    }

    suspend fun scrub() {
        shareArticleDownloadHelper.cleanup()

        val orphanedSectionStubs: List<SectionStub> = getOrphanedSectionStubs()
        for (sectionStub in orphanedSectionStubs) {
            val section = sectionRepository.get(sectionStub.key)
            section?.let { deleteSection(it) }
        }

        val orphanedMomentStubs: List<MomentStub> = getOrphanedMomentStubs()
        for (momentStub in orphanedMomentStubs) {
            val moment = momentRepository.get(momentStub.issueKey)
            moment?.let { deleteMoment(it) }
        }

        val orphanedFrontPageJoins: List<IssuePageJoin> = getOrphanedFrontPageJoins()
        for (frontPageJoin in orphanedFrontPageJoins) {
            deleteFrontPage(frontPageJoin)
        }

        val orphanedPageStubs: List<PageStub> = getOrphanedPageStubs()
        for (pageStub in orphanedPageStubs) {
            val page = pageRepository.get(pageStub.pdfFileName)
            page?.let { deletePage(page) }
        }

        val orphanedArticleStubs: List<ArticleStub> = getOrphanedArticleStubs()
        for (articleStub in orphanedArticleStubs) {
            val article = articleRepository.get(articleStub.articleFileName)
            article?.let { deleteArticle(it) }
        }

        // ResourceInfo
        val orphanedResourceInfo: List<ResourceInfoStub> = getOrphanedResourceInfoStubs()
        for (resourceInfoStub in orphanedResourceInfo) {
            val resourceInfo: ResourceInfo =
                resourceInfoRepository.resourceInfoStubToResourceInfo(resourceInfoStub)
            deleteResourceInfo(resourceInfo)
        }

        // Must be called after all types that might reference an Image (Article, Section, Moment)
        val orphanedImages: List<Image> = getOrphanedImages()
        for (image in orphanedImages) {
            deleteImage(image)
        }

        // Must be called after all types that might reference an Audio (Article, Section)
        val orphanedAudioStubs: List<AudioStub> = getOrphanedAudioStubs()
        for (audioStub in orphanedAudioStubs) {
            val audio = audioRepository.get(audioStub.fileName)
            audio?.let { deleteAudio(it) }
        }

        // Must be called after all types that might reference an FileEntry (*)
        val orphanedFileEntries: List<FileEntry> = getOrphanedFileEntries()
        for (fileEntry in orphanedFileEntries) {
            deleteFile(fileEntry)
        }
    }

    private suspend fun getOrphanedSectionStubs(): List<SectionStub> {
        return appDatabase.sectionDao().getOrphanedSections()
    }

    private suspend fun deleteSection(section: Section) {
        val sectionStub = SectionStub(section)

        // As orphaned sections are not part of any Issue, they should not have any Bookmarks,
        // but if we have some, we should not delete it
        val hasBookmarkedArticle = section.articleList.any { it.bookmarked }
        if (hasBookmarkedArticle) {
            log.warn("Found Section without an Issue that has a bookmarked Article: ${section.key}")
            return
        }

        try {
            appDatabase.withTransaction {
                appDatabase.sectionImageJoinDao().deleteRelationToSection(section.key)
                appDatabase.sectionArticleJoinDao().deleteRelationToSection(section.key)

                appDatabase.sectionDao().delete(sectionStub)
            }
        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete orphaned Section: ${section.key}", e)
            return
        }

        deleteFile(section.sectionHtml)
        section.podcast?.let { deleteAudio(it) }

        section.imageList.forEach { deleteImage(it) }
        section.articleList.forEach { deleteArticle(it) }
    }

    private suspend fun getOrphanedImages(): List<Image> {
        val orphanedImageStubNames = appDatabase.imageStubDao()
            .getOrphanedImageStubs()
            .map { it.fileEntryName }
            .filter { it != defaultNavDrawerFileName }
        return appDatabase.imageDao().getByNames(orphanedImageStubNames)
    }

    private suspend fun deleteImage(image: Image) {
        try {
            val imageStub = ImageStub(image)
            appDatabase.imageStubDao().delete(imageStub)

            val fileEntry = FileEntry(image)
            deleteFile(fileEntry)

        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete orphaned Image: ${image.name}", e)
        }
    }

    private suspend fun getOrphanedArticleStubs(): List<ArticleStub> {
        return appDatabase.articleDao().getOrphanedArticles()
    }

    private suspend fun deleteArticle(article: Article) {
        if (article.bookmarked) {
            log.warn("Found orphaned Article that has a bookmark: ${article.key}")
            return
        }

        val articleStub = ArticleStub(article)

        try {
            appDatabase.withTransaction {
                appDatabase.articleAuthorImageJoinDao().deleteRelationToArticle(article.key)
                appDatabase.articleImageJoinDao().deleteRelationToArticle(article.key)

                appDatabase.articleDao().delete(articleStub)
            }

        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete orphaned Article: ${article.key}", e)
            return
        }

        deleteFile(article.articleHtml)
        article.audio?.let { deleteAudio(it) }

        article.imageList.forEach { deleteImage(it) }
        article.authorList.mapNotNull { it.imageAuthor }.forEach { deleteFile(it) }
    }

    private suspend fun getOrphanedAudioStubs(): List<AudioStub> {
        return appDatabase.audioDao().getOrphanedAudios()
    }

    private suspend fun deleteAudio(audio: Audio) {
        try {
            appDatabase.audioDao().delete(audio.file.name)
            deleteFile(audio.file)

        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete orphaned Audio: ${audio.file.name}", e)
        }
    }

    private suspend fun getOrphanedPageStubs(): List<PageStub> {
        return appDatabase.pageDao()
            .getOrphanedPages()
    }

    private suspend fun deletePage(page: Page) {
        val pageStub = PageStub(page)
        try {
            appDatabase.pageDao().delete(pageStub)
            deleteFile(page.pagePdf)

        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete orphaned Page: ${page.pagePdf}", e)
        }
    }

    private suspend fun getOrphanedFrontPageJoins(): List<IssuePageJoin> {
        return appDatabase.issuePageJoinDao()
            .getOrphanedFrontPages()
            .sortedBy { it.issueDate }
            .dropLast(KEEP_LATEST_MOMENTS_COUNT)
    }

    private suspend fun deleteFrontPage(frontPageJoin: IssuePageJoin) {
        appDatabase.issuePageJoinDao().delete(frontPageJoin)
        pageRepository.get(frontPageJoin.pageKey)?.let { deletePage(it) }
    }

    private suspend fun getOrphanedMomentStubs(): List<MomentStub> {
        return appDatabase.momentDao()
            .getOrphanedMoments()
            .sortedBy { it.issueDate }
            .dropLast(KEEP_LATEST_MOMENTS_COUNT)
    }

    private suspend fun deleteMoment(moment: Moment) {
        try {
            appDatabase.withTransaction {
                appDatabase.momentImageJoinJoinDao().deleteRelationToMoment(
                    moment.issueFeedName,
                    moment.issueDate,
                    moment.issueStatus
                )
                appDatabase.momentCreditJoinDao().deleteRelationToMoment(
                    moment.issueFeedName,
                    moment.issueDate,
                    moment.issueStatus
                )
                appDatabase.momentFilesJoinDao().deleteRelationToMoment(
                    moment.issueFeedName,
                    moment.issueDate,
                    moment.issueStatus
                )

                val momentStub = MomentStub(moment)
                appDatabase.momentDao().delete(momentStub)
            }
        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete orphaned Moment ${moment.momentKey}", e)
            return
        }

        moment.momentList.forEach { deleteFile(it) }
        moment.imageList.forEach { deleteImage(it) }
        moment.creditList.forEach { deleteImage(it) }
    }

    private suspend fun getOrphanedResourceInfoStubs(): List<ResourceInfoStub> {
        val resourceInfos = appDatabase.resourceInfoDao()
            .getAll()
            .sortedByDescending { it.resourceVersion }

        val latestDownloadedResourceInfo =
            resourceInfos.find { it.dateDownload != null }
                ?: return emptyList()

        // Keep ResourceInfos that have the same resource version, or have a newer one, then the latest downloaded ResourceInfo
        return resourceInfos.dropWhile { it.resourceVersion >= latestDownloadedResourceInfo.resourceVersion }
    }

    private suspend fun deleteResourceInfo(resourceInfo: ResourceInfo) {
        val resourceInfoStub = ResourceInfoStub(resourceInfo)
        try {
            appDatabase.resourceInfoFileEntryJoinDao().deleteRelationToResourceInfo(resourceInfo.resourceVersion)
            appDatabase.resourceInfoDao().delete(resourceInfoStub)

        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete ResourceInfo Metadata: ${resourceInfo.resourceVersion}", e)
            return
        }

        resourceInfo.resourceList.forEach { deleteFile(it) }
    }

    private suspend fun getOrphanedFileEntries(): List<FileEntry> {
        return appDatabase.fileEntryDao().getOrphanedFileEntries()
    }

    private suspend fun deleteFile(fileEntry: FileEntry) {
        try {
            // Ensure that the file contents are only deleted if the FileEntry is not referenced as a ForeignKey anymore.
            appDatabase.withTransaction {
                fileEntryRepository.delete(fileEntry)
                deleteFileFromDisk(fileEntry)
            }
        } catch (e: SQLiteConstraintException) {
            log.warn("Could not delete FileEntry Metadata: $fileEntry", e)

        } catch (e: FileDeletionException) {
            log.error(e.message ?: "", e.cause)
            SentryWrapper.captureMessage("Could not delete file from disk")
        }
    }

    private suspend fun deleteFileFromDisk(fileEntry: FileEntry) {
        if (fileEntry.storageLocation == StorageLocation.NOT_STORED) {
            return
        }

        try {
            storageService.deleteFile(fileEntry)
        } catch (e: IOException) {
            throw FileDeletionException("Could not delete file from disk: $fileEntry", e)
        }
    }

    private class FileDeletionException(message: String, cause: Throwable) :
        Exception(message, cause)
}