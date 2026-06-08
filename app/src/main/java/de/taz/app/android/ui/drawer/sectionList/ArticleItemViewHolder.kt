package de.taz.app.android.ui.drawer.sectionList

import android.text.Spannable
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.singletons.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.CoroutineContext


class ArticleItemViewHolder(
    parent: ViewGroup,
    private val onArticleClick: (Article) -> Unit,
    private val onBookmarkClick: (Article) -> Unit,
    private val onAudioEnqueueClick: (Article, Boolean) -> Unit,
    private val getBookmarkStateFlow: (String) -> Flow<Boolean>
) :
    RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.fragment_drawer_article_item,
            parent,
            false
        )
    ), CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main
    private val audioPlayerService: AudioPlayerService =
        AudioPlayerService.getInstance(itemView.context.applicationContext)

    private var rootLayout: ConstraintLayout =
        itemView.findViewById(R.id.drawer_article_item_layout)
    private var titleTextView: TextView = itemView.findViewById(R.id.fragment_drawer_article_title)
    private var teaserTextView: TextView =
        itemView.findViewById(R.id.fragment_drawer_article_teaser)
    private var authorAndReadMinutesTextView: TextView =
        itemView.findViewById(R.id.fragment_drawer_article_author_and_read_minutes)
    private var articleImageView: ImageView =
        itemView.findViewById(R.id.fragment_drawer_article_image)
    private var bookmarkIconImageView: ImageView =
        itemView.findViewById(R.id.fragment_drawer_article_bookmark_icon)
    private var audioEnqueueImageView: ImageView =
        itemView.findViewById(R.id.fragment_drawer_audio_enqueue)
    private var audioEnqueuedImageView: ImageView =
        itemView.findViewById(R.id.fragment_drawer_audio_enqueued)

    private val fileHelper = StorageService.getInstance(parent.context.applicationContext)

    private var bindJob: Job? = null

    fun bind(sectionDrawerItem: SectionDrawerItem.Item, currentKey: String?) {
        bindJob?.cancel()
        val article = sectionDrawerItem.article
        titleTextView.text = article.title

        val backgroundColorRes = if (article.key == currentKey) {
            R.color.navigation_drawer_highlight_background
        } else {
            R.color.navigation_drawer_background
        }
        rootLayout.setBackgroundColor(ContextCompat.getColor(itemView.context, backgroundColorRes))

        teaserTextView.isVisible = !article.teaser.isNullOrBlank()
        teaserTextView.text = article.teaser

        // get the author(s) from the article
        val authorsString = article.authorList.map { it.name }.distinct().joinToString(", ")
        val readMinutesString = article.readMinutes?.let {
            itemView.context.getString(R.string.read_minutes, it)
        } ?: ""

        val twoStyledSpannable =
            constructAuthorsAndReadMinutesSpannable(authorsString, readMinutesString)

        authorAndReadMinutesTextView.apply {
            isVisible = twoStyledSpannable.isNotEmpty()
            setText(twoStyledSpannable, TextView.BufferType.SPANNABLE)
        }

        updateArticleImage(article)

        bookmarkIconImageView.setOnClickListener {
            onBookmarkClick(article)
        }

        itemView.setOnClickListener {
            onArticleClick(article)
        }

        if (article.audio == null) {
            audioEnqueueImageView.isVisible = false
            audioEnqueuedImageView.isVisible = false
        }

        bindJob = launch {
            if (article.audio != null) {
                launch {
                    audioPlayerService.isInPlaylistFlow(article).collect { isEnqueued ->
                        audioEnqueuedImageView.isVisible = isEnqueued
                        audioEnqueueImageView.isVisible = !isEnqueued
                        val clickListener =
                            View.OnClickListener { onAudioEnqueueClick(article, isEnqueued) }
                        audioEnqueuedImageView.setOnClickListener(clickListener)
                        audioEnqueueImageView.setOnClickListener(clickListener)
                    }
                }
            }

            launch {
                getBookmarkStateFlow(article.key).collect { isBookmarked ->
                    val iconRes =
                        if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
                    bookmarkIconImageView.setImageResource(iconRes)
                }
            }
        }
    }

    private fun constructAuthorsAndReadMinutesSpannable(
        authors: String,
        readMinutes: String
    ): SpannableString {
        val authorsAndReadMinutesString: String
        val authorSpanStart: Int
        val authorSpanEnd: Int
        val readMinutesSpanStart: Int
        val readMinutesSpanEnd: Int

        when {
            readMinutes.isBlank() && authors.isBlank() -> {
                authorsAndReadMinutesString = ""
                authorSpanStart = 0
                authorSpanEnd = 0
                readMinutesSpanStart = 0
                readMinutesSpanEnd = 0
            }

            readMinutes.isBlank() -> {
                authorsAndReadMinutesString = authors
                authorSpanStart = 0
                authorSpanEnd = authors.length
                readMinutesSpanStart = 0
                readMinutesSpanEnd = 0
            }

            authors.isBlank() -> {
                authorsAndReadMinutesString = readMinutes
                authorSpanStart = 0
                authorSpanEnd = 0
                readMinutesSpanStart = 0
                readMinutesSpanEnd = readMinutes.length
            }

            else -> {
                authorsAndReadMinutesString = "$authors $readMinutes"
                authorSpanStart = 0
                authorSpanEnd = authors.length
                readMinutesSpanStart = authorSpanEnd + 1
                readMinutesSpanEnd = readMinutesSpanStart + readMinutes.length
            }
        }

        val text = SpannableString(authorsAndReadMinutesString)

        if (authorSpanStart < authorSpanEnd) {
            text.setSpan(
                TextAppearanceSpan(
                    itemView.context,
                    R.style.TextAppearance_App_Drawer_Sections_Article_Meta_Author
                ),
                0,
                authors.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (readMinutesSpanStart < readMinutesSpanEnd) {
            text.setSpan(
                TextAppearanceSpan(
                    itemView.context,
                    R.style.TextAppearance_App_Drawer_Sections_Article_Meta_ReadMinutes
                ),
                readMinutesSpanStart,
                readMinutesSpanEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return text
    }

    private fun showArticleImageView(imagePath: String) {
        articleImageView.isVisible = true
        Glide.with(itemView.context)
            .load(imagePath)
            .into(articleImageView)
    }

    private fun updateArticleImage(article: Article) {
        if (showArticleIcon(article)) return

        val imagePath = article.imageList
            .firstOrNull { it.dateDownload != null }
            ?.let { fileHelper.getAbsolutePath(it) }
            ?.takeIf { File(it).exists() }

        if (imagePath != null) {
            showArticleImageView(imagePath)
        } else {
            articleImageView.isVisible = false
            Glide.with(itemView.context).clear(articleImageView)
        }
    }

    private fun showArticleIcon(article: Article): Boolean {
        val iconPath = article.icon?.let { fileHelper.getAbsolutePath(it) }
            ?.takeIf { File(it).exists() } ?: return false
        showArticleImageView(iconPath)
        return true
    }
}