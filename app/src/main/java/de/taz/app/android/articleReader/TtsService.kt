package de.taz.app.android.articleReader

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.text.Html.fromHtml
import android.text.Spanned
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.BreakIterator
import java.util.*
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TtsService(private val applicationContext: Context) {
    companion object {
        private const val BLOCK_END_TAG = "### BLOCK"
        private val TEXT_LOCALE = Locale.GERMAN
        private const val SILENCE_AFTER_SENTENCE = 200L
        // After each block (paragraph) of text a longer silence is added before continuing reading
        // the next sentence. This silence is in addition to the silence already added after each
        // sentence as defined with SILENCE_AFTER_SENTENCE
        private const val SILENCE_AFTER_BLOCK = 700L
        private const val UNKNOWN_UTTERANCE_ID = -1
        private const val SILENT_UTTERANCE_ID_PREFIX = "SILENCE-"
    }

    private val log by Log

    private val storageService = StorageService.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    private val breakIterator = BreakIterator.getSentenceInstance(TEXT_LOCALE)
    private var tts: TextToSpeech? = null
    private var ttsInitError: TtsError? = null

    private var article: ArticleStub? = null

    private var utteranceIdPrefix: String = "### NO_PREFIX"
    private var utterances: List<String> = emptyList()
    private var position = 0
    private var _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    suspend fun setArticle(article: ArticleStub) {
        // Rethrow any error encountered during the initialization
        ttsInitError?.let {
            throw it
        }

        if (article.key != this.article?.key) {
            utteranceIdPrefix = article.key + "-"
            prepare(article)
        }
    }

    suspend fun init() {
        if (tts != null) {
            return
        }

        try {
            val newTts = createTtsInstance(applicationContext)
            val result = newTts.setLanguage(TEXT_LOCALE)
            if (result == TextToSpeech.LANG_MISSING_DATA) {
                throw TtsError("Language data is missing for $TEXT_LOCALE")
            }
            if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                throw TtsError("Language is not supported $TEXT_LOCALE")
            }
            newTts.setOnUtteranceProgressListener(progressListener)

            ttsInitError = null
            tts = newTts
        } catch (error: TtsError) {
            // Store the error to rethrow it when an article is set
            ttsInitError = error
        }
    }


    private suspend fun prepare(articleStub: ArticleStub) {
        val file = withContext(Dispatchers.IO) {
            val fileEntry = fileEntryRepository.getOrThrow(articleStub.articleFileName)
            storageService.getFile(fileEntry)
        }
        if (file != null) {
            prepare(file)
        }
        this.article = articleStub
    }

    /**
     * Load the Articles HTML and prepare the utterance queue
     * This will not start reading this article, nor stopping any previous TTS.
     */
    private suspend fun prepare(htmlSrc: File) {
        val html = withContext(Dispatchers.IO) {
            htmlSrc.readText()
        }

        val body = html.extractHtmlBody()
        val bodyText: Spanned = body.htmlToSpanned()
        val bodyTextString: String = bodyText.toString()
            .removeUnicodeChars()
            .removeSuccessiveEmptyBlocks()
        val paragraphs = bodyTextString.split("\\n")

        utterances = paragraphs.flatMap { paragraph ->
            when {
                paragraph.isEmpty() -> listOf(BLOCK_END_TAG)
                else -> splitSentences(paragraph)
            }
        }
        position = 0
    }

    fun release() {
        article = null
        utterances = emptyList()
        position = 0
    }

    fun shutdown() {
        release()
        tts?.shutdown()
        tts = null
        ttsInitError = null
    }

    fun play() {
        // ensure the position is re-set to the start
        position = 0
        resume()
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun resume() {
        log.verbose("resume from $position")
        val tts = requireNotNull(tts) { "TextToSpeech has to be initialized before playing" }

        // stop any previous playings
        tts.stop()

        // enqueue sentences and pauses
        for (i in position..utterances.lastIndex) {
            val utterance = utterances[i]
            val id = makeUtteranceId(i)
            when (utterance) {
                BLOCK_END_TAG -> tts.playSilentUtterance(SILENCE_AFTER_BLOCK, QUEUE_ADD, id)
                else -> {
                    tts.speak(utterance, QUEUE_ADD, null, id)
                    tts.playSilentUtterance(SILENCE_AFTER_SENTENCE, QUEUE_ADD, makeSilenceId(i))
                }
            }
        }
    }


    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            val index = getUtteranceIndex(utteranceId)
            log.verbose("onStart($utteranceId / $index)")

            if (index != UNKNOWN_UTTERANCE_ID) {
                position = index
                _isPlaying.value = true
            }
        }

        override fun onDone(utteranceId: String) {
            val index = getUtteranceIndex(utteranceId)
            log.verbose("onDone($utteranceId / $index)")

            if (index == utterances.lastIndex) {
                _isPlaying.value = false
            }
        }

        override fun onError(utteranceId: String) {
            log.error("Error playing utterance with id: $utteranceId")
        }
    }

    private fun makeUtteranceId(index: Int): String {
        return utteranceIdPrefix + index.toString()
    }

    /**
     * Returns UNKNOWN_UTTERANCE_ID or the positive index of the utterance played by the utteranceId
     */
    private fun getUtteranceIndex(utteranceId: String): Int {
        if (!utteranceId.startsWith(utteranceIdPrefix)) {
            return UNKNOWN_UTTERANCE_ID
        }

        return try {
            utteranceId.substring(utteranceIdPrefix.length).toInt()
        } catch (e: NumberFormatException) {
            UNKNOWN_UTTERANCE_ID
        }
    }

    private fun makeSilenceId(index: Int): String {
        return SILENT_UTTERANCE_ID_PREFIX + utteranceIdPrefix + index.toString()
    }

    // region: HTML and String helper functions
    private val extractHtmlBodyPattern = Pattern.compile("<body[^>]*>(.*)</body>", Pattern.DOTALL)
    private fun String.extractHtmlBody(): String {
        val matcher = extractHtmlBodyPattern.matcher(this)
        if (matcher.find()) {
            val body = matcher.group(1)
            return body ?: ""
        }
        return ""
    }

    /**
     * Removes special unicode chars from the text:
     * - Soft Hyphens (U+00AD) - which are added on the backend to teaser texts
     * - Symbols (U+FFFC) - which are inserted in place of images by androids fromHtml()
     */
    private fun String.removeUnicodeChars(): String {
        return this.replace(Regex("[\u00ad\ufffc]"), "")
    }

    private val successiveNewlinePattern = Pattern.compile("\n\n+")
    private fun String.removeSuccessiveEmptyBlocks(): String {
        return successiveNewlinePattern.matcher(this).replaceAll("\n\n")
    }

    /**
     * Remove all HTML elements and return a [Spanned] string.
     * HTML block-level elements will be separated by two newlines ("\n").
     */
    private fun String.htmlToSpanned(): Spanned {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            fromHtml(this)
        } else {
            fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun splitSentences(paragraph: String): List<String> {
        val result = mutableListOf<String>()
        breakIterator.setText(paragraph)
        var start = breakIterator.first()
        var end = breakIterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = paragraph.substring(start, end)
            result.add(sentence)

            start = end
            end = breakIterator.next()
        }
        return result
    }
    // endregion
}

private suspend fun createTtsInstance(context: Context): TextToSpeech {
    return suspendCoroutine { cont ->
        var tts: TextToSpeech? = null
        val initCallback = OnInitListener {
            when (it) {
                TextToSpeech.SUCCESS -> cont.resume(requireNotNull(tts))
                else -> cont.resumeWithException(TtsError("TextToSpeech is not supported by the system"))
            }
        }
        tts = TextToSpeech(context, initCallback)
    }
}