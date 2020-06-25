package de.taz.app.android

import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.*

val testImage = Image(
    "imageName",
    StorageType.resource,
    1869L,
    "goldman",
    1940L,
    "Ⓐ",
    ImageType.picture,
    alpha = 1f,
    resolution = ImageResolution.high,
    downloadedStatus = DownloadStatus.pending
)

val testFileEntry = FileEntry(
    "fileName",
    StorageType.issue,
    161L,
    "sha256",
    0L,
    "folder",
    DownloadStatus.pending
)


val testSection = Section(
    FileEntry(
        "sectionFileEntry",
        StorageType.issue,
        1337L,
        "?",
        0,
        "folder",
        DownloadStatus.pending
    ),
    "1313-12-13",
    "a section",
    SectionType.articles,
    testImage,
    emptyList(),
    emptyList(),
    null,
    DownloadStatus.pending
)

val testIssue = Issue(
    feedName = "taz",
    date = "2019-10-17",
    moment = Moment(),
    key =  null,
    baseUrl = "https://example.com",
    status = IssueStatus.regular,
    minResourceVersion = 23,
    imprint = null,
    sectionList = listOf(testSection),
    isWeekend = false,
    pageList = emptyList(),
    dateDownload = null,
    downloadedStatus = DownloadStatus.pending
)
val testIssues = listOf(testIssue)

val testIssueStub = IssueStub(
    feedName = "feed",
    date = "01-01-1900",
    key = "key",
    baseUrl = "https://example.com",
    status = IssueStatus.regular,
    minResourceVersion = 1312,
    isWeekend = false,
    dateDownload = null,
    downloadedStatus = DownloadStatus.pending
)

val testArticle = Article(
    testFileEntry,
    "taz",
    "1999-01-01",
    "super Artikel",
    "hervorragender artikel …",
    "https://example.com",
    null,
    emptyList(),
    emptyList(),
    emptyList(),
    ArticleType.STANDARD,
    false,
    0,
    0,
    DownloadStatus.pending
)