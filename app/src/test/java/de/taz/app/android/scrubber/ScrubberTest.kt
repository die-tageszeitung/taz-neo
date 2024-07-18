package de.taz.app.android.scrubber

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.KEEP_LATEST_MOMENTS_COUNT
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.AudioRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.test.Fixtures
import de.taz.test.Fixtures.copyWithFileName
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ScrubberTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    private lateinit var scrubber: Scrubber
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var issueRepository: IssueRepository
    private lateinit var audioRepository: AudioRepository
    private lateinit var resourceInfoRepository: ResourceInfoRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var pageRepository: PageRepository
    private lateinit var momentRepository: MomentRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var mockAuthHelper: AuthHelper

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        AppDatabase.inject(db)

        mockAuthHelper = mock {
            onBlocking { isValid() }.doReturn(false)
        }
        AuthHelper.inject(mockAuthHelper)


        scrubber = Scrubber(context)
        fileEntryRepository = FileEntryRepository.getInstance(context)
        issueRepository = IssueRepository.getInstance(context)
        audioRepository = AudioRepository.getInstance(context)
        resourceInfoRepository = ResourceInfoRepository.getInstance(context)
        articleRepository = ArticleRepository.getInstance(context)
        pageRepository = PageRepository.getInstance(context)
        momentRepository = MomentRepository.getInstance(context)
        sectionRepository = SectionRepository.getInstance(context)
        imageRepository = ImageRepository.getInstance(context)

        SectionRepository.getInstance(context).apply {
            injectDefaultNavButton(Fixtures.fakeNavButton)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Orphaned Audio is removed`() = runTest {
        val audio = Fixtures.audio
        audioRepository.saveInternal(audio)

        scrubber.scrub()

        assertNull(audioRepository.get(audio.file.name))
    }

    @Test
    fun `Audio is kept if referenced from a Page`() = runTest {
        val audio = Fixtures.audio
        val issue = Fixtures.issueBase.copy(
            pageList = listOf(
                Fixtures.pageBase.copy(
                    podcast = audio
                )
            )
        )
        issueRepository.save(issue)

        scrubber.scrub()

        assertEquals(audio, audioRepository.get(audio.file.name))
    }

    @Test
    fun `Audio is kept if referenced from a Section`() = runTest {
        val audio = Fixtures.audio
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    podcast = audio
                )
            )
        )
        issueRepository.save(issue)

        scrubber.scrub()

        assertEquals(audio, audioRepository.get(audio.file.name))
    }

    @Test
    fun `Audio is kept if referenced from an Article`() = runTest {
        val audio = Fixtures.audio
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    articleList = listOf(
                        Fixtures.articleBase.copy(
                            audio = audio
                        )
                    )
                )
            )
        )
        issueRepository.save(issue)

        scrubber.scrub()

        assertEquals(audio, audioRepository.get(audio.file.name))
    }

    @Test
    fun `Latest ResourceInfo is kept even if it is not downloaded`() = runTest {
        val dateDownload = requireNotNull(DateHelper.stringToDate("2000-12-13"))

        val downloadedResourceInfo = Fixtures.resourceInfoBase.copy(
            resourceVersion = 1,
            dateDownload = dateDownload
        )
        val latestResourceInfo = Fixtures.resourceInfoBase.copy(
            resourceVersion = 2
        )
        resourceInfoRepository.save(downloadedResourceInfo)
        resourceInfoRepository.save(latestResourceInfo)

        scrubber.scrub()

        assertEquals(
            listOf(latestResourceInfo, downloadedResourceInfo),
            resourceInfoRepository.getAll()
        )
    }

    @Test
    fun `ResourceInfo older then the latest downloaded are removed`() = runTest {
        val dateDownload = requireNotNull(DateHelper.stringToDate("2000-12-13"))
        val file = Fixtures.fileEntry
        val outdatedResourceInfo = Fixtures.resourceInfoBase.copy(
            resourceVersion = 1,
            dateDownload = dateDownload,
            resourceList = listOf(file),
        )
        val latestResourceInfo = Fixtures.resourceInfoBase.copy(
            resourceVersion = 2,
            dateDownload = dateDownload,
            resourceList = listOf(file),
        )

        resourceInfoRepository.save(outdatedResourceInfo)
        resourceInfoRepository.save(latestResourceInfo)

        scrubber.scrub()

        assertEquals(
            listOf(latestResourceInfo),
            resourceInfoRepository.getAll()
        )
    }

    @Test
    fun `Orphaned Article is removed`() = runTest {
        val article = Fixtures.articleBase
        articleRepository.saveInternal(article)

        scrubber.scrub()

        assertNull(articleRepository.get(article.key))
    }


    @Test
    fun `Article with Bookmark is kept`() = runTest {
        val article = Fixtures.articleBase.copy(
            bookmarkedTime = requireNotNull(DateHelper.stringToDate("2000-12-13"))
        )
        articleRepository.saveInternal(article)

        scrubber.scrub()

        assertEquals(article, articleRepository.get(article.key))
    }

    @Test
    fun `Article is kept if referenced from a Section`() = runTest {
        val article = Fixtures.articleBase
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    articleList = listOf(
                        article
                    )
                )
            )
        )
        issueRepository.save(issue)

        scrubber.scrub()

        assertEquals(article, articleRepository.get(article.key))
    }

    @Test
    fun `Article is kept if referenced as an Imprint`() = runTest {
        val article = Fixtures.articleBase
        val issue = Fixtures.issueBase.copy(
            imprint = article,
        )
        issueRepository.save(issue)

        scrubber.scrub()

        assertEquals(article, articleRepository.get(article.key))
    }

    @Test
    fun `Orphaned Page is removed`() = runTest {
        val page = Fixtures.pageBase
        pageRepository.saveInternal(page)

        scrubber.scrub()

        assertNull(pageRepository.get(page.pagePdf.name))
    }

    @Test
    fun `Page is kept if referenced from an Issue`() = runTest {
        val page = Fixtures.pageBase
        val issue = Fixtures.issueBase.copy(
            pageList = listOf(page)
        )
        issueRepository.save(issue)

        scrubber.scrub()

        assertEquals(page, pageRepository.get(page.pagePdf.name))
    }

    @Test
    fun `All but the latest KEEP_LATEST_MOMENTS_COUNT orphaned Moments are deleted`() = runTest {
        val momentsToScrub = (1..2).map { i ->
            val year = 1900 + i
            Fixtures.momentBase.copy(
                issueDate = "$year-01-01"
            )
        }

        val momentsToKeep = (1..KEEP_LATEST_MOMENTS_COUNT).map { i ->
            val year = 2000 + i
            Fixtures.momentBase.copy(
                issueDate = "$year-01-01"
            )
        }

        momentsToScrub.forEach { momentRepository.save(it) }
        momentsToKeep.forEach { momentRepository.save(it) }

        scrubber.scrub()

        momentsToScrub.forEach {
            assertNull(momentRepository.get(it.momentKey))
        }
        momentsToKeep.forEach {
            assertEquals(it, momentRepository.get(it.momentKey))
        }
    }

    @Test
    fun `Moment is kept if referenced from an Issue`() = runTest {
        val moment = Fixtures.momentBase.copy(
            issueDate = "1900-01-01"
        )
        val issue = Fixtures.issueBase.copy(
            date = "1900-01-01",
            moment = moment
        )
        val momentsToKeep = (1..KEEP_LATEST_MOMENTS_COUNT).map { i ->
            val year = 2000 + i
            Fixtures.momentBase.copy(
                issueDate = "$year-01-01"
            )
        }

        issueRepository.save(issue)
        momentsToKeep.forEach { momentRepository.save(it) }

        scrubber.scrub()

        assertEquals(moment, momentRepository.get(moment.momentKey))
    }

    @Test
    fun `All but the latest KEEP_LATEST_MOMENTS_COUNT orphaned FrontPages are deleted`() = runTest {
        val frontPagesToScrub = (1..2).map { i ->
            val year = 1900 + i
            val issueDate = "$year-01-01"
            val issueKey = Fixtures.issueBase.issueKey.copy(
                date = issueDate
            )
            val page = Fixtures.pageBase.copyWithFileName("frontpage-$issueDate.pdf")

            issueKey to page
        }

        val frontPagesToKeep = (1..KEEP_LATEST_MOMENTS_COUNT).map { i ->
            val year = 2000 + i
            val issueDate = "$year-01-01"
            val issueKey = Fixtures.issueBase.issueKey.copy(
                date = issueDate
            )
            val page = Fixtures.pageBase.copyWithFileName("frontpage-$issueDate.pdf")

            issueKey to page
        }

        frontPagesToScrub.forEach { (issueKey, page) ->
            pageRepository.saveFrontPage(page, issueKey)
        }
        frontPagesToKeep.forEach { (issueKey, page) ->
            pageRepository.saveFrontPage(page, issueKey)
        }

        scrubber.scrub()

        frontPagesToScrub.forEach { (issueKey, _) ->
            assertNull(pageRepository.getFrontPage(issueKey))
        }
        frontPagesToKeep.forEach { (issueKey, page) ->
            assertEquals(page, pageRepository.getFrontPage(issueKey))
        }
    }

    @Test
    fun `FrontPage is kept if referenced from an Issue`() = runTest {
        val frontPage = Fixtures.pageBase.copyWithFileName("frontpage-with-issue.pdf")
        val issue = Fixtures.issueBase.copy(
            date = "1900-01-01",
            pageList = listOf(frontPage)
        )

        val frontPagesToKeep = (1..KEEP_LATEST_MOMENTS_COUNT).map { i ->
            val year = 2000 + i
            val issueDate = "$year-01-01"
            val issueKey = Fixtures.issueBase.issueKey.copy(
                date = issueDate
            )
            val page = Fixtures.pageBase.copyWithFileName("frontpage-$issueDate.pdf")

            issueKey to page
        }

        issueRepository.save(issue)
        frontPagesToKeep.forEach { (issueKey, page) ->
            pageRepository.saveFrontPage(page, issueKey)
        }

        scrubber.scrub()

        assertEquals(frontPage, pageRepository.getFrontPage(issue.issueKey))
    }

    @Test
    fun `NavButton Image is kept if Section is scrubbed`() = runTest {
        // Given a navButton
        // that is (fake) stored on the disk
        val navButton = Fixtures.fakeNavButton.copy(
            storageLocation = StorageLocation.INTERNAL,
            moTime = Fixtures.fakeNavButton.moTime + 1
        )
        val navButtonFileEntry = FileEntry(navButton)
        imageRepository.saveInternal(navButton)

        // and referenced from the latest Resourced
        val resourceInfo = Fixtures.resourceInfoBase.copy(
            resourceList = listOf(navButtonFileEntry)
        )
        resourceInfoRepository.save(resourceInfo)

        // and part of an orphaned Section
        val section = Fixtures.sectionBase.copy(
            navButton = navButton
        )
        sectionRepository.saveInternal(section)

        // When the scrubber deletes the orphaned Section
        scrubber.scrub()

        // Then the section is deleted
        assertNull(sectionRepository.get(section.key))

        // but the navButton Image and FileEntry are kept and its storage location is not changed
        assertEquals(navButtonFileEntry, fileEntryRepository.get(navButtonFileEntry.name))
        assertEquals(navButton, imageRepository.get(navButton.name))
    }

    @Test
    fun `Less valuable Issues are deleted`() = runTest {
        // Given
        val issuePublic = Fixtures.issueBase.copy(status = IssueStatus.public)
        val issueDemo = Fixtures.issueBase.copy(status = IssueStatus.demo)
        val issueRegular = Fixtures.issueBase.copy(status = IssueStatus.regular)

        issueRepository.save(issuePublic)
        issueRepository.save(issueDemo)
        issueRepository.save(issueRegular)

        assertNotNull(issueRepository.getStub(issuePublic.issueKey))
        assertNotNull(issueRepository.getStub(issueDemo.issueKey))
        assertNotNull(issueRepository.getStub(issueRegular.issueKey))

        // When
        scrubber.scrub()

        // Then
        assertNull(issueRepository.getStub(issuePublic.issueKey))
        assertNull(issueRepository.getStub(issueDemo.issueKey))
        assertNotNull(issueRepository.getStub(issueRegular.issueKey))
    }

    @Test
    fun `Public and demo Issues are deleted if logged in with valid subscription`() = runTest {
        // Given
        val issuePublic = Fixtures.issueBase.copy(status = IssueStatus.public, date = "2000-12-13")
        val issueDemo = Fixtures.issueBase.copy(status = IssueStatus.demo, date = "2001-12-13")
        val issueRegular = Fixtures.issueBase.copy(status = IssueStatus.regular, date = "2002-12-13")

        issueRepository.save(issuePublic)
        issueRepository.save(issueDemo)
        issueRepository.save(issueRegular)

        assertNotNull(issueRepository.getStub(issuePublic.issueKey))
        assertNotNull(issueRepository.getStub(issueDemo.issueKey))
        assertNotNull(issueRepository.getStub(issueRegular.issueKey))

        // When
        wheneverBlocking { mockAuthHelper.isValid() }.doReturn(true)
        scrubber.scrub()

        // Then
        assertNull(issueRepository.getStub(issuePublic.issueKey))
        assertNull(issueRepository.getStub(issueDemo.issueKey))
        assertNotNull(issueRepository.getStub(issueRegular.issueKey))
    }
}