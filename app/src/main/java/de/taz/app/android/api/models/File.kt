package de.taz.app.android.api.models

interface File {
    val name: String
    val storageType: StorageType
    val moTime: String
    val sha256: String
    val size: Int
}

enum class StorageType {
    issue,
    global,
    public,
    resource
}
