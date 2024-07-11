package de.taz.app.android.scrubber

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.StorageService
import de.taz.test.Fixtures
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ScrubberFileEntryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    private lateinit var scrubber: Scrubber
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var issueRepository: IssueRepository
    private lateinit var storageService: StorageService
    private lateinit var resourceInfoRepository: ResourceInfoRepository

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        AppDatabase.inject(db)

        scrubber = Scrubber(context)
        fileEntryRepository = FileEntryRepository.getInstance(context)
        issueRepository = IssueRepository.getInstance(context)
        storageService = StorageService.getInstance(context)
        resourceInfoRepository = ResourceInfoRepository.getInstance(context)

        SectionRepository.getInstance(context).apply {
            injectDefaultNavButton(Fixtures.fakeNavButton)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Orphaned FileEntry Metadata is removed`() = runTest {
        val fileEntry1 = Fixtures.fileEntry

        fileEntryRepository.save(fileEntry1)
        assertEquals(fileEntry1, fileEntryRepository.get(fileEntry1.name))

        scrubber.scrub()

        assertNull(fileEntryRepository.get(fileEntry1.name))
    }

    @Test
    fun `Orphaned FileEntry File is deleted`() = runTest {
        val fileEntry1 = createFileEntryWithContent("test01.txt")

        fileEntryRepository.save(fileEntry1)
        assertTrue(storageService.ensureFileExists(fileEntry1))

        scrubber.scrub()

        assertFalse(storageService.ensureFileExists(fileEntry1))
    }

    @Test
    fun `Orphaned FileEntry is kept if file could not be deleted due to an IOException`() = runTest {
        val mockStorageService = mock<StorageService> {
            onBlocking { deleteFile(any()) } doAnswer {
                throw IOException("Mocked exception")
            }
        }
        // Mock the StorageService and initialize a new Scrubber that will use it
        StorageService.inject(mockStorageService)
        val scrubber = Scrubber(context)

        val fileEntry1 = Fixtures.fileEntry.copy(storageLocation = StorageLocation.INTERNAL)
        fileEntryRepository.save(fileEntry1)

        scrubber.scrub()

        assertEquals(fileEntry1, fileEntryRepository.get(fileEntry1.name))
    }

    @Test
    fun `FileEntry is kept if referenced from a Section`() = runTest {
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(Fixtures.sectionBase)
        )
        val sectionFileEntry = Fixtures.sectionBase.sectionHtml
        issueRepository.save(issue)
        assertEquals(sectionFileEntry, fileEntryRepository.get(sectionFileEntry.name))

        scrubber.scrub()

        assertEquals(sectionFileEntry, fileEntryRepository.get(sectionFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from a Section Image`() = runTest {
        val image = Fixtures.image
        val imageFileEntry = FileEntry(image)
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    imageList = listOf(Fixtures.image)
                )
            )
        )
        issueRepository.save(issue)
        assertEquals(imageFileEntry, fileEntryRepository.get(imageFileEntry.name))

        scrubber.scrub()

        assertEquals(imageFileEntry, fileEntryRepository.get(imageFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from an Article`() = runTest {
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    articleList = listOf(Fixtures.articleBase)
                )
            )
        )
        val articleFileEntry = Fixtures.articleBase.articleHtml
        issueRepository.save(issue)
        assertEquals(articleFileEntry, fileEntryRepository.get(articleFileEntry.name))

        scrubber.scrub()

        assertEquals(articleFileEntry, fileEntryRepository.get(articleFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from an Article Author`() = runTest {
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    articleList = listOf(
                        Fixtures.articleBase.copy(
                            authorList = listOf(
                                Fixtures.authorWithImage01
                            )
                        )
                    )
                )
            )
        )
        val authorImageFileEntry = requireNotNull(Fixtures.authorWithImage01.imageAuthor)
        issueRepository.save(issue)
        assertEquals(authorImageFileEntry, fileEntryRepository.get(authorImageFileEntry.name))

        scrubber.scrub()

        assertEquals(authorImageFileEntry, fileEntryRepository.get(authorImageFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from an Article Image`() = runTest {
        val image = Fixtures.image
        val imageFileEntry = FileEntry(image)
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    articleList = listOf(
                        Fixtures.articleBase.copy(
                            imageList = listOf(image)
                        )
                    )
                )
            )
        )
        issueRepository.save(issue)
        assertEquals(imageFileEntry, fileEntryRepository.get(imageFileEntry.name))

        scrubber.scrub()

        assertEquals(imageFileEntry, fileEntryRepository.get(imageFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from an Audio`() = runTest {
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    podcast = Fixtures.audio
                )
            )
        )
        val audioFileEntry = Fixtures.audio.file
        issueRepository.save(issue)
        assertEquals(audioFileEntry, fileEntryRepository.get(audioFileEntry.name))

        scrubber.scrub()

        assertEquals(audioFileEntry, fileEntryRepository.get(audioFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from a Page`() = runTest {
        val issue = Fixtures.issueBase.copy(
            pageList = listOf(Fixtures.pageBase)
        )
        val pageFileEntry = Fixtures.pageBase.pagePdf
        issueRepository.save(issue)
        assertEquals(pageFileEntry, fileEntryRepository.get(pageFileEntry.name))

        scrubber.scrub()

        assertEquals(pageFileEntry, fileEntryRepository.get(pageFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from a MomentFile`() = runTest {
        val issue = Fixtures.issueBase.copy(
            moment = Fixtures.momentBase.copy(
                momentList = listOf(Fixtures.fileEntry)
            )
        )
        val momentFileEntry = Fixtures.fileEntry
        issueRepository.save(issue)
        assertEquals(momentFileEntry, fileEntryRepository.get(momentFileEntry.name))

        scrubber.scrub()

        assertEquals(momentFileEntry, fileEntryRepository.get(momentFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from a MomentImage`() = runTest {
        val issue = Fixtures.issueBase.copy(
            moment = Fixtures.momentBase.copy(
                imageList = listOf(Fixtures.image)
            )
        )
        val momentImageFileEntry = FileEntry(Fixtures.image)
        issueRepository.save(issue)
        assertEquals(momentImageFileEntry, fileEntryRepository.get(momentImageFileEntry.name))

        scrubber.scrub()

        assertEquals(momentImageFileEntry, fileEntryRepository.get(momentImageFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from a MomentCredit`() = runTest {
        val issue = Fixtures.issueBase.copy(
            moment = Fixtures.momentBase.copy(
                creditList = listOf(Fixtures.image)
            )
        )
        val momentCreditFileEntry = FileEntry(Fixtures.image)
        issueRepository.save(issue)
        assertEquals(momentCreditFileEntry, fileEntryRepository.get(momentCreditFileEntry.name))

        scrubber.scrub()

        assertEquals(momentCreditFileEntry, fileEntryRepository.get(momentCreditFileEntry.name))
    }

    @Test
    fun `FileEntry is kept if referenced from the ResourceInfo`() = runTest {
        val fileEntry = Fixtures.fileEntry
        val resourceInfo = Fixtures.resourceInfoBase.copy(
            resourceList = listOf(fileEntry)
        )
        fileEntryRepository.save(fileEntry)
        resourceInfoRepository.save(resourceInfo)

        scrubber.scrub()

        assertEquals(fileEntry, fileEntryRepository.get(fileEntry.name))
    }

    private suspend fun createFileEntryWithContent(name: String): FileEntry {
        val dataString = "Hello World"
        val dataBytes = dataString.toByteArray()
        val now = Calendar.getInstance()

        val fileEntry = FileEntry(
            name,
            StorageType.global,
            now.timeInMillis,
            "",
            dataBytes.size.toLong(),
            "",
            now.time,
            "testData",
            StorageLocation.INTERNAL
        )

        storageService.writeFile(fileEntry, ByteReadChannel(dataBytes))

        return fileEntry
    }

}