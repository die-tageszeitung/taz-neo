package de.taz.app.android.util

import android.content.Context

/**
 * Singleton handling authentication
 */
class AuthHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val preferences = applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var token: String
        set(token) = preferences.edit().putString("token", token).apply()
        get() = preferences.getString("token", "") ?: ""

}