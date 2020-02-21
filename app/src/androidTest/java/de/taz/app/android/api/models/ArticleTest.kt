package de.taz.app.android.api.models

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.taz.app.android.IssueTestUtil
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ArticleTest {

    private lateinit var db: AppDatabase
    private lateinit var articleRepository: ArticleRepository

    private val issue = IssueTestUtil.getIssue()
    private val sections = issue.sectionList
    private val section = sections.first()
    private val article = section.articleList.first()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        val fileEntryRepository = FileEntryRepository.createInstance(context)
        fileEntryRepository.appDatabase = db

        articleRepository = ArticleRepository.createInstance(context)
        articleRepository.appDatabase = db
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getAllFiles() {
        articleRepository.save(article)
        val fileList = article.getAllFiles()

        assertTrue(fileList.filter { it == article.articleHtml }.size == 1)

        article.imageList
            .filter { it.name.contains(".norm.") }
            .forEach { fileEntry ->
                assertTrue(fileList.filter { it == fileEntry }.size == 1)
            }
    }
}