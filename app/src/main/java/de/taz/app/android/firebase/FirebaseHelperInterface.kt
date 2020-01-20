package de.taz.app.android.firebase

interface FirebaseHelperInterface {

    var firebaseToken: String?
    var hasTokenBeenSent: Boolean
    val isPush: Boolean
}