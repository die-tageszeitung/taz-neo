package de.taz.app.android.api.dto

import de.taz.app.android.api.models.*

open class IssueDto(
    open val date: String,
    open val key: String? = null,
    open val baseUrl: String,
    open val status: IssueStatus,
    open val minResourceVersion: Int,
    open val zipName: String? = null,
    open val zipPdfName: String? = null,
    open val navButton: NavButton? = null,
    open val imprint: Article,
    open val fileList: List<String>,
    open val fileListPdf: List<String>,
    open val sectionList: List<Section>? = null,
    open val pageList: List<Page>? = null
)
