package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ImageStub

@Dao
interface ImageStubDao: BaseDao<ImageStub> {

    @Query("SELECT * FROM Image WHERE fileEntryName == :name")
    suspend fun getByName(name: String): ImageStub?

    @Query("""
        SELECT Image.* FROM Image 
         WHERE NOT EXISTS ( SELECT 1 FROM ArticleImageJoin WHERE ArticleImageJoin.imageFileName = Image.fileEntryName )
           AND NOT EXISTS ( SELECT 1 FROM ArticleAuthor WHERE ArticleAuthor.authorFileName = Image.fileEntryName )
           AND NOT EXISTS ( SELECT 1 FROM MomentImageJoin WHERE MomentImageJoin.momentFileName = Image.fileEntryName )
           AND NOT EXISTS ( SELECT 1 FROM MomentCreditJoin WHERE MomentCreditJoin.momentFileName = Image.fileEntryName ) 
           AND NOT EXISTS ( SELECT 1 FROM SectionImageJoin WHERE SectionImageJoin.imageFileName = Image.fileEntryName )
           AND NOT EXISTS ( SELECT 1 FROM ResourceInfoFileEntryJoin WHERE ResourceInfoFileEntryJoin.fileEntryName = Image.fileEntryName )
    """)
    suspend fun getOrphanedImageStubs(): List<ImageStub>

}