package de.taz.app.android.api.models

data class Issue (
    val date: String,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val imprint: Article,
    val fileList: List<String>,
    val fileListPdf: List<String>,
    val sectionList: List<Section>? = null,
    val pageList: List<Page>? = null
)

enum class IssueStatus {
    regular,
    demo,
    locked,
    public
}