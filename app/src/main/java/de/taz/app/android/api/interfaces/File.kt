package de.taz.app.android.api.interfaces

interface File {
    val name: String
    val storageType: StorageType
    val moTime: Long
    val sha256: String
    val size: Int
}

enum class StorageType {
    issue,
    global,
    public,
    resource
}
