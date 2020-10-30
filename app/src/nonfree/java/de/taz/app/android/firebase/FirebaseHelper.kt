package de.taz.app.android.firebase

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.util.SingletonHolder


const val PREFERENCES_FCM = "fcm"
const val PREFERENCES_FCM_TOKEN = "fcm token"
const val PREFERENCES_FCM_TOKEN_SENT = "fcm token sent"

@Mockable
class FirebaseHelper @VisibleForTesting(otherwise=VisibleForTesting.PRIVATE) constructor(applicationContext: Context) : FirebaseHelperInterface {

    companion object : SingletonHolder<FirebaseHelper, Context>(::FirebaseHelper)

    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_FCM, Context.MODE_PRIVATE)

    private val firebaseTokenLiveData =
        SharedPreferenceStringLiveData(
            preferences, PREFERENCES_FCM_TOKEN, ""
        )
    override var firebaseToken: String?
        get() = if (firebaseTokenLiveData.value.isEmpty()) null else firebaseTokenLiveData.value
        set(value) = firebaseTokenLiveData.postValue(value)

    override val isPush: Boolean
        get() = firebaseToken?.isNotEmpty() ?: false

    private val firebaseTokenHasBeenSentLiveData =
        SharedPreferenceBooleanLiveData(
            preferences, PREFERENCES_FCM_TOKEN_SENT, false
        )

    override var hasTokenBeenSent: Boolean
        get() = firebaseTokenHasBeenSentLiveData.value
        set(value) {
            firebaseTokenHasBeenSentLiveData.postValue(value)
        }

    init {
        log.info("firebase registration token: $firebaseToken")
    }
}