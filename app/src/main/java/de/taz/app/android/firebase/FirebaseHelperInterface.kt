package de.taz.app.android.firebase

import de.taz.app.android.annotation.Mockable

@Mockable
interface FirebaseHelperInterface {

    var firebaseToken: String?
    var hasTokenBeenSent: Boolean
    val isPush: Boolean
}