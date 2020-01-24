package de.taz.app.android.api.interfaces

import android.net.Uri
import de.taz.app.android.api.models.FileEntry

interface Shareable {

    fun getLink(): String?

    fun getShareable(): Pair<String?, FileEntry?>

}