package de.taz.app.android.scrubber

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.test.Fixtures
import de.taz.test.RobolectricTestApplication
import de.taz.test.SingletonTestUtil
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RobolectricTestApplication::class)
class ScrubberImageTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    private lateinit var scrubber: Scrubber
    private lateinit var issueRepository: IssueRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var resourceInfoRepository: ResourceInfoRepository

    @Before
    fun setUp() {
        SingletonTestUtil.resetAll()

        context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        AppDatabase.inject(db)

        scrubber = Scrubber(context)
        issueRepository = IssueRepository.getInstance(context)
        imageRepository = ImageRepository.getInstance(context)
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
    fun `Orphaned Image is removed`() = runTest {
        val image = Fixtures.image
        imageRepository.saveInternal(image)

        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertNull(imageRepository.get(image.name))
    }

    @Test
    fun `Image is kept if referenced from an Article`() = runTest {
        val image = Fixtures.image
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
        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertEquals(image, imageRepository.get(image.name))
    }

    @Test
    fun `Image is kept if referenced from an Article Author`() = runTest {
        val image = Fixtures.authorImage01
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
        issueRepository.save(issue)

        // Note that the Author Image is not stored as an Image in the DB for some reasons
        assertNull(imageRepository.get(image.name))

        imageRepository.saveInternal(image)
        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertEquals(image, imageRepository.get(image.name))
    }


    @Test
    fun `Image is kept if referenced from a MomentImage`() = runTest {
        val image = Fixtures.image
        val issue = Fixtures.issueBase.copy(
            moment = Fixtures.momentBase.copy(
                imageList = listOf(image)
            )
        )
        issueRepository.save(issue)
        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertEquals(image, imageRepository.get(image.name))
    }

    @Test
    fun `Image is kept if referenced from a MomentCredit`() = runTest {
        val image = Fixtures.image
        val issue = Fixtures.issueBase.copy(
            moment = Fixtures.momentBase.copy(
                creditList = listOf(image)
            )
        )
        issueRepository.save(issue)
        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertEquals(image, imageRepository.get(image.name))
    }

    @Test
    fun `Image is kept if referenced from a Section`() = runTest {
        val image = Fixtures.image
        val issue = Fixtures.issueBase.copy(
            sectionList = listOf(
                Fixtures.sectionBase.copy(
                    imageList = listOf(image)
                )
            )
        )
        issueRepository.save(issue)
        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertEquals(image, imageRepository.get(image.name))
    }

    // This will only happen for the defaultNavButton
    @Test
    fun `Image is kept if its FileEntry is referenced from the ResourceInfo`() = runTest {
        val image = Fixtures.image
        val fileEntry = FileEntry(image)
        val resourceInfo = Fixtures.resourceInfoBase.copy(
            resourceList = listOf(fileEntry)
        )
        resourceInfoRepository.save(resourceInfo)
        imageRepository.saveInternal(image)
        assertEquals(image, imageRepository.get(image.name))

        scrubber.scrub()

        assertEquals(image, imageRepository.get(image.name))
    }
}