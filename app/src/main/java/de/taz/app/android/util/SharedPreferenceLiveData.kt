package de.taz.app.android.util

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData

abstract class SharedPreferenceLiveData<T>(
    val sharedPreferences: SharedPreferences,
    val key: String,
    private val defaultValue: T
) : MutableLiveData<T>() {

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == this.key) {
                value = getValueFromPreferences(key, defaultValue)
            }
        }

    abstract fun getValueFromPreferences(key: String, defValue: T): T

    override fun onActive() {
        super.onActive()
        value = getValueFromPreferences(key, defaultValue)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }
}

class SharedPreferenceIntLiveData(sharedPrefs: SharedPreferences, key: String, defValue: Int) :
    SharedPreferenceLiveData<Int>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: Int): Int =
        sharedPreferences.getInt(key, defValue)

    override fun setValue(value: Int?) {
        super.setValue(value)
        value?.let {
            sharedPreferences.edit().putInt(key, value).commit()
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

    override fun setValue(value: String?) {
        super.setValue(value)
        value?.let {
            sharedPreferences.edit().putString(key, value).commit()
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

    override fun setValue(value: Boolean?) {
        super.setValue(value)
        value?.let {
            sharedPreferences.edit().putBoolean(key, value).commit()
        }
    }
}

class SharedPreferenceFloatLiveData(sharedPrefs: SharedPreferences, key: String, defValue: Float) :
    SharedPreferenceLiveData<Float>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: Float): Float =
        sharedPreferences.getFloat(key, defValue)

    override fun setValue(value: Float?) {
        super.setValue(value)
        value?.let {
            sharedPreferences.edit().putFloat(key, value).commit()
        }
    }

}

class SharedPreferenceLongLiveData(
    sharedPrefs: SharedPreferences, key: String, defValue: Long
) : SharedPreferenceLiveData<Long>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: Long): Long =
        sharedPreferences.getLong(key, defValue)

    override fun setValue(value: Long?) {
        super.setValue(value)
        value?.let {
            sharedPreferences.edit().putLong(key, value).commit()
        }
    }

}

class SharedPreferenceStringSetLiveData(
    sharedPrefs: SharedPreferences, key: String, defValue: Set<String>
) : SharedPreferenceLiveData<Set<String>>(sharedPrefs, key, defValue) {

    override fun getValueFromPreferences(key: String, defValue: Set<String>): Set<String> {
        return sharedPreferences.getStringSet(key, defValue) ?: emptySet()
    }

    override fun setValue(value: Set<String>?) {
        super.setValue(value)
        value?.let {
            sharedPreferences.edit().putStringSet(key, value).commit()
        }
    }

}

