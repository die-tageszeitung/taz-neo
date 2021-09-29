package de.taz.app.android.util

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.AuthStatus
import io.sentry.Sentry

abstract class SharedPreferenceLiveData<T>(
    val sharedPreferences: SharedPreferences,
    val key: String,
    private val defaultValue: T
) : MutableLiveData<T>() {

    override fun getValue(): T {
        return super.getValue() ?: getValueFromPreferences(key, defaultValue)
    }

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == this.key) {
                postValue(getValueFromPreferences(key, defaultValue))
            }
        }

    abstract fun getValueFromPreferences(key: String, defValue: T): T

    override fun onActive() {
        super.onActive()
        postValue(getValueFromPreferences(key, defaultValue))
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }

    abstract fun saveValueToPreferences(value: T)

    override fun setValue(value: T) {
        saveValueToPreferences(value)
        super.setValue(value)
    }
}

class SharedPreferenceIntLiveData(sharedPrefs: SharedPreferences, key: String, defValue: Int) :
    SharedPreferenceLiveData<Int>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: Int): Int =
        sharedPreferences.getInt(key, defValue)

    override fun saveValueToPreferences(value: Int) {
        with (sharedPreferences.edit()) {
            putInt(key, value)
            apply()
        }
    }
}

class SharedPreferenceStringLiveData(
    sharedPrefs: SharedPreferences,
    key: String,
    defValue: String
) :
    SharedPreferenceLiveData<String>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: String): String =
        sharedPreferences.getString(key, defValue) ?: defValue

    override fun saveValueToPreferences(value: String) {
        with (sharedPreferences.edit()) {
            putString(key, value)
            apply()
        }
    }
}

class SharedPreferenceStorageLocationLiveData(
    sharedPrefs: SharedPreferences,
    key: String,
    defValue: StorageLocation
) :
    SharedPreferenceLiveData<StorageLocation>(sharedPrefs, key, defValue) {
    val log by Log

    override fun getValueFromPreferences(key: String, defValue: StorageLocation): StorageLocation {
        val ordinal = try {
            sharedPreferences.getInt(key, StorageLocation.INTERNAL.ordinal)
        } catch (e: Exception) {
            log.error("Bad state in shared prefenrence for StorageLocation")
            Sentry.captureException(e)
            with (sharedPreferences.edit()) {
                putInt(key, defValue.ordinal)
                apply()
            }
            defValue.ordinal
        }
        return StorageLocation.values()[ordinal]
    }

    override fun saveValueToPreferences(value: StorageLocation) {
        with (sharedPreferences.edit()) {
            putInt(key, value.ordinal)
            apply()
        }
    }
}

class SharedPreferenceBooleanLiveData(
    sharedPrefs: SharedPreferences,
    key: String,
    defValue: Boolean
) :
    SharedPreferenceLiveData<Boolean>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: Boolean): Boolean =
        sharedPreferences.getBoolean(key, defValue)

    override fun saveValueToPreferences(value: Boolean) {
        with (sharedPreferences.edit()) {
            putBoolean(key, value)
            apply()
        }
    }
}

class SharedPreferencesAuthStatusLiveData(
    sharedPrefs: SharedPreferences, key: String, defValue: AuthStatus
): SharedPreferenceLiveData<AuthStatus>(sharedPrefs, key, defValue) {


    override fun getValueFromPreferences(key: String, defValue: AuthStatus): AuthStatus {
        val name = sharedPreferences.getString(key, defValue.name)
        return AuthStatus.valueOf(name ?: defValue.name)
    }

    override fun saveValueToPreferences(value:AuthStatus) {
        with (sharedPreferences.edit()) {
            putString(key, value.name)
            apply()
        }
    }

}

