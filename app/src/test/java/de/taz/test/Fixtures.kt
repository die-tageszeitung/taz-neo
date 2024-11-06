package de.taz.test

import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.AudioSpeaker
import de.taz.app.android.api.models.Author
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.api.models.ResourceInfo
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.api.models.StorageType

object Fixtures {

    val feedName = "testFeed"
    val issueDate = "2000-12-13"
    val issueStatus = IssueStatus.regular

    val fileEntry: FileEntry = FileEntry(
        "file01.html",
        StorageType.global,
        moTime = 0L,
        "sha256",
        size = 0L,
        dateDownload = null,
        "path",
        StorageLocation.NOT_STORED
    )

    val image: Image = Image(
        fileEntry.copy(
            name = "image01.png",
            storageType = StorageType.issue,
        )
        , ImageStub(
            "image01.png", ImageType.picture, alpha = 0f, ImageResolution.normal
        )
    )

    /**
     * Fake default navButton.
     * The actual default navButton is configured via the Android resources and not accessible without a context
     */
    val fakeNavButton: Image = image.copy(
        name = "navButton.taz.normal.png",
        type = ImageType.button,
        alpha = 1f,
    )

    val audio: Audio = Audio(
        fileEntry.copy(name = "audio01.mp3"),
        playtime = null,
        duration = null,
        AudioSpeaker.UNKNOWN,
        breaks = null
    )

    /**
     * Base Moment without any relations
     */
    val momentBase: Moment = Moment(
        feedName,
        issueDate,
        issueStatus,
        "baseUrl",
        imageList = emptyList(),
        creditList = emptyList(),
        momentList = emptyList(),
        dateDownload = null
    )

    /**
     * Base Issue without any relations
     */
    val issueBase: Issue = Issue(
        feedName,
        issueDate,
        null,
        momentBase,
        "key",
        "baseUrl",
        issueStatus,
        minResourceVersion = 0,
        imprint = null,
        isWeekend = false,
        sectionList = emptyList(),
        pageList = emptyList(),
        "moTime",
        dateDownload = null,
        dateDownloadWithPages = null,
        lastDisplayableName = null,
        lastPagePosition = null,
        lastViewedDate = null
    )

    /**
     * Base Article without any relations
     */
    val articleBase: Article = Article(
        fileEntry.copy(name = "article01.html"),
        feedName,
        issueDate,
        "title",
        "teaser",
        onlineLink = "https://example.com",
        audio = null,
        pageNameList = emptyList(),
        imageList = emptyList(),
        authorList = emptyList(),
        mediaSyncId = null,
        chars = 200,
        words = 10,
        readMinutes = 1,
        ArticleType.STANDARD,
        bookmarkedTime = null,
        position = 0,
        percentage = 0,
        dateDownload = null,
        pdf = null,
    )

    val pageBase: Page = Page(
        fileEntry.copy("page01.pdf"),
        "title",
        "pagina",
        PageType.left,
        listOf(Frame(0f, 0f, 1f, 1f, "https://example.com")),
        dateDownload = null,
        "baseUrl",
        podcast = null
    )

    /**
     * Base Section without any relations
     */
    val sectionBase: Section = Section(
        fileEntry.copy(name = "section01.html"),
        issueDate,
        "title",
        SectionType.articles,
        fakeNavButton,
        articleList = emptyList(),
        imageList = emptyList(),
        "extendedTitle",
        dateDownload = null,
        podcast = null
    )

    val resourceInfoBase: ResourceInfo = ResourceInfo(
        resourceVersion = 1,
        "resourceBaseUrl",
        "resourceZip",
        resourceList = emptyList(),
        dateDownload = null
    )

    val authorBase: Author = Author(
        "Author 01",
        imageAuthor = null,
    )

    val authorImage01 = image.copy(
        name = "author01-image.png",
        storageType = StorageType.global
    )
    val authorWithImage01: Author = authorBase.copy(
        name = "Author With Image 01", imageAuthor = FileEntry(authorImage01)
    )

    val authorImage02 = authorImage01.copy(name = "author02-image.png")
    val authorWithImage02: Author = authorBase.copy(
        name = "Author With Image 02", imageAuthor = FileEntry(authorImage02)
    )

    val article01 = articleBase.copyWithFileName("article01.html")
        .copy(
            imageList = listOf(
                image.copy(name = "article01-image01.png"),
                image.copy(name = "article01-image02.png"),
            ),
            authorList = listOf(
                authorBase,
                authorWithImage01,
            ),
            audio = audio.copyWithFileName("article01-audio.mp3"),
        )

    val article02 = articleBase.copyWithFileName("article02.html")
        .copy(
            imageList = listOf(
                image.copy(name = "article02-image01.png"),
                image.copy(name = "article02-image02.png"),
            ),
            authorList = listOf(
                authorBase,
                authorWithImage02,
            ),
            audio = audio.copyWithFileName("article02-audio.mp3"),
        )

    fun Article.copyWithFileName(name: String) = copy(
        articleHtml = articleHtml.copy(name = name)
    )

    fun Section.copyWithFileName(name: String) = copy(
        sectionHtml = sectionHtml.copy(name = name)
    )

    fun Page.copyWithFileName(name: String) = copy(
        pagePdf.copy(name = name)
    )

    fun Audio.copyWithFileName(name: String) = copy(
        fileEntry.copy(name = name)
    )
}