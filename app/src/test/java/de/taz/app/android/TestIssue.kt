package de.taz.app.android

import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.*

val testFileEntry = FileEntry(
    "fileName",
    StorageType.issue,
    161L,
    "sha256",
    0L,
    "folder"
)


val testSection = Section(
    FileEntry(
        "sectionFileEntry",
        StorageType.issue,
        1337L,
        "?",
        0,
        "folder"
    ),
    "1313-12-13",
    "a section",
    SectionType.articles,
    emptyList(),
    emptyList()
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
    pageList = emptyList()
)
val testIssues = listOf(testIssue)

val testIssueStub = IssueStub(
    feedName = "feed",
    date = "01-01-1900",
    key = "key",
    baseUrl = "https://example.com",
    status = IssueStatus.regular,
    minResourceVersion = 1312,
    isWeekend = false
)

val testArticle = Article(
    testFileEntry,
    "taz",
    "1999-01-01",
    "super Artikel",
    "hervorragender artikel …",
    "https://example.com",
    null
)