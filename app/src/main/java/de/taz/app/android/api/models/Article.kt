package de.taz.app.android.api.models

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.taz.app.android.api.interfaces.AudioPlayerPlayable
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.monkey.isPublicArticle
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.persistence.join.SectionArticleJoin
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import java.util.Date

data class Article(
    @Embedded val articleStub: ArticleStub,

    @Relation(
        parentColumn = "articleFileName",
        entityColumn = "name"
    )
    val articleHtml: FileEntry?,

    @Relation(
        entity = ImageStub::class,
        parentColumn = "iconFileName",
        entityColumn = "fileEntryName"
    )
    val iconWithFile: ImageWithFile?,

    @Relation(
        parentColumn = "pdfFileName",
        entityColumn = "name"
    )
    val pdf: FileEntry?,

    @Relation(
        entity = AudioStub::class,
        parentColumn = "audioFileName",
        entityColumn = "fileName"
    )
    val audioWithFile: AudioWithFile?,

    @Relation(
        entity = ImageStub::class,
        parentColumn = "articleFileName",
        entityColumn = "fileEntryName",
        associateBy = Junction(
            value = ArticleImageJoin::class,
            parentColumn = "articleFileName",
            entityColumn = "imageFileName"
        )
    )
    val imagesWithFiles: List<ImageWithFile>,

    @Relation(
        entity = ArticleAuthorImageJoin::class,
        parentColumn = "articleFileName",
        entityColumn = "articleFileName"
    )
    val authorJoins: List<AuthorJoinWithFile> = emptyList(),

    @Relation(
        parentColumn = "articleFileName",
        entityColumn = "articleFileName"
    )
    val sectionArticleJoin: SectionArticleJoin? = null,

    @Relation(
        entity = SectionStub::class,
        parentColumn = "articleFileName",
        entityColumn = "sectionFileName",
        associateBy = Junction(
            value = SectionArticleJoin::class,
            parentColumn = "articleFileName",
            entityColumn = "sectionFileName"
        )
    )
    val section: SectionStub? = null
) : WebViewDisplayable, AudioPlayerPlayable {
    val articleFileName: String
        get() = articleStub.articleFileName
    val issueFeedName: String
        get() = articleStub.issueFeedName
    val issueDate: String
        get() = articleStub.issueDate
    override val key: String
        get() = articleStub.articleFileName
    val articleType: ArticleType
        get() = articleStub.articleType
    override val dateDownload: Date?
        get() = articleStub.dateDownload
    val mediaSyncId: Int?
        get() = articleStub.mediaSyncId
    val title: String?
        get() = articleStub.title
    val teaser: String?
        get() = articleStub.teaser
    val readMinutes: Int?
        get() = articleStub.readMinutes
    val onlineLink: String?
        get() = articleStub.onlineLink
    val pageNameList: List<String>
        get() = articleStub.pageNameList
    val bookmarkedTime: Date?
        get() = articleStub.bookmarkedTime
    val position: Int
        get() = articleStub.position
    val percentage: Int
        get() = articleStub.percentage
    val chars: Int?
        get() = articleStub.chars
    val words: Int?
        get() = articleStub.words
    override val audioFileName: String? // TODO why we need override here?
        get() = articleStub.audioFileName
    val pdfFileName: String?
        get() = articleStub.pdfFileName
    val iconFileName: String?
        get() = articleStub.iconFileName
    fun getAuthorNames(): String {
        return authorJoins.mapNotNull { it.authorJoin.authorName }.joinToString(", ")
    }

    override suspend fun previous(applicationContext: Context): String? {
        return ArticleRepository.getInstance(applicationContext).previousArticleKey(key)
    }

    override suspend fun next(applicationContext: Context): String? {
        return ArticleRepository.getInstance(applicationContext).nextArticleKey(key)
    }

    override suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance(applicationContext).getIssueStubByImprintFileName(this.key)
        } else {
            section?.getIssueStub(applicationContext)
        }
    }

    val indexInSection: Int?
        get() = sectionArticleJoin?.index?.plus(1)

    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return ArticleRepository.getInstance(applicationContext).getDownloadDate(articleStub)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        return ArticleRepository.getInstance(applicationContext).setDownloadDate(articleStub, date)
    }

    override suspend fun getAllFiles(applicationContext: Context): List<FileEntry> {
        val files = mutableListOf<FileEntry>()

        // article HTML file
        articleHtml?.let { files.add(it) }

        // add images
        files.addAll(imagesWithFiles.filter { it.imageStub.resolution == ImageResolution.normal }
            .mapNotNull { it.fileEntry })
        // author images
        files.addAll(authorJoins.mapNotNull { it.fileEntry })
        // icon image
        icon?.let { files.add(FileEntry(it)) }
        return files.distinct()
    }

    override fun getDownloadTag(): String {
        return this.key
    }

    val audio: Audio? by lazy {
        audioWithFile?.let {
            it.fileEntry?.let { file ->
                Audio(
                    file,
                    it.audioStub.playtime,
                    it.audioStub.duration,
                    it.audioStub.speaker,
                    it.audioStub.breaks,
                )
            }
        }
    }

    val imageList: List<Image> by lazy {
        imagesWithFiles.mapNotNull {
            it.fileEntry?.let { fileEntry ->
                Image(
                    fileEntry,
                    it.imageStub
                )
            }
        }
    }

    val icon: Image? by lazy {
        iconWithFile?.let {
            Image(
                it.fileEntry!!,
                it.imageStub
            )
        }
    }

    val authorList: List<Author> by lazy {
        authorJoins.map { Author(it.authorJoin.authorName, it.fileEntry) }
    }

    val bookmarked by lazy {
        bookmarkedTime != null
    }

    /**
     * If articles file name contains "public" we assume the corresponding issue has [IssueStatus.public]
     * otherwise we assume an issue with status [IssueStatus.regular]
     */
    fun guessIssueStatusByArticleFileName(): IssueStatus {
        return if (key.isPublicArticle()) {
            IssueStatus.public
        } else {
            IssueStatus.regular
        }
    }

    override val audioPlayerPlayableKey: String
        get() = key

    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    val hasAudio: Boolean
        get() = audio != null
}

data class AudioWithFile(
    @Embedded val audioStub: AudioStub,
    @Relation(
        parentColumn = "fileName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
) {
    constructor(audio: Audio) : this(AudioStub(audio), audio.file)
}

data class ImageWithFile(
    @Embedded val imageStub: ImageStub,
    @Relation(
        parentColumn = "fileEntryName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
) {
    // Helper constructor to wrap an Image model
    constructor(image: Image) : this(ImageStub(image), FileEntry(image))
}

data class AuthorJoinWithFile(
    @Embedded val authorJoin: ArticleAuthorImageJoin,
    @Relation(
        parentColumn = "authorFileName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
) {
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    constructor(articleName: String, author: Author, index: Int, id: Int) : this(
        authorJoin = ArticleAuthorImageJoin(
            articleName,
            author.name,
            author.imageAuthor?.name,
            index,
            id,
        ),
        author.imageAuthor,
    )
}

enum class ArticleType {
    STANDARD, IMPRINT, PODCAST;
}
