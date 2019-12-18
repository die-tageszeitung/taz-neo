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
    "taz",
    "2019-10-17",
    Moment(),
    null,
    "https://example.com",
    IssueStatus.regular,
    23,
    null,
    null,
    null,
    null,
    emptyList(),
    emptyList(),
    listOf(testSection),
    emptyList()
)
val testIssues = listOf(testIssue)

val testIssueStub = IssueStub(
    "feed",
    "01-01-1900",
    "key",
    "https://example.com",
    IssueStatus.regular,
    1312,
    null,
    null,
    null,
    emptyList(),
    emptyList()
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