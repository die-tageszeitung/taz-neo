package de.taz.app.android.persistence.pojo

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AudioStub
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin

data class ArticleWithDetails(
    @Embedded val articleStub: ArticleStub,

    @Relation(
        parentColumn = "articleFileName",
        entityColumn = "name"
    )
    val articleHtml: FileEntry?,

    @Relation(
        parentColumn = "pdfFileName",
        entityColumn = "name"
    )
    val pdf: FileEntry?,

    @Relation(
        entity = ImageStub::class,
        parentColumn = "iconFileName",
        entityColumn = "fileEntryName",
    )
    val icon: ImageWithFile?,

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
    val authorJoins: List<AuthorJoinWithFile>
)

data class AudioWithFile(
    @Embedded val audioStub: AudioStub,
    @Relation(
        parentColumn = "fileName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
)

data class ImageWithFile(
    @Embedded val imageStub: ImageStub,
    @Relation(
        parentColumn = "fileEntryName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
)

data class AuthorJoinWithFile(
    @Embedded val authorJoin: ArticleAuthorImageJoin,
    @Relation(
        parentColumn = "authorFileName",
        entityColumn = "name"
    )
    val fileEntry: FileEntry?
)
