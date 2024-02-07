package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.AudioStub
import de.taz.app.android.util.SingletonHolder

class AudioRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<AudioRepository, Context>(::AudioRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    suspend fun get(audioFileName: String): Audio? {
        val audioStub = appDatabase.audioDao().get(audioFileName) ?: return null
        val audioFileEntry = fileEntryRepository.get(audioFileName) ?: return null

        return Audio(
            audioFileEntry,
            audioStub.playtime,
            audioStub.duration,
            audioStub.speaker,
            audioStub.breaks
        )
    }

    /**
     * Save the [Audio] to the database and replace any existing [Audio] with the same key.
     *
     * This method must be called as part of a transaction,
     * for example when saving an [Article].
     */
    suspend fun saveInternal(audio: Audio) {
        fileEntryRepository.save(audio.file)

        val audioStub = AudioStub(audio)
        appDatabase.audioDao().insertOrReplace(audioStub)
    }

    /**
     * Try to delete the Audio entry, if it is not referenced from any other database entry.
     */
    suspend fun tryDelete(audio: Audio) {
        try {
            delete(AudioStub(audio))
        } catch (e: SQLiteConstraintException) {
            log.info("Did not delete audio ${audio.file.name} because it is still referenced", e)
        }
    }

    suspend fun delete(audio: Audio) {
        delete(AudioStub(audio))
    }

    suspend fun delete(audioStub: AudioStub) {
        appDatabase.audioDao().delete(audioStub)
        fileEntryRepository.delete(audioStub.fileName)
    }
}