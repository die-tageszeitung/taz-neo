package de.taz.app.android.firebase

import android.content.Context
import de.taz.app.android.util.SingletonHolder

class FirebaseHelper private constructor(
    @Suppress("UNUSED_PARAMETER") applicationContext: Context
) : FirebaseHelperInterface {

    companion object : SingletonHolder<FirebaseHelper, Context>(::FirebaseHelper)

    override var firebaseToken: String? = null
    override val isPush: Boolean = false
    override var hasTokenBeenSent = false
}