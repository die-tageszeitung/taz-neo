package de.taz.app.android.api.models

open class FileEntry(
    override val name: String,
    override val storageType: StorageType,
    override val moTime: String,
    override val sha256: String,
    override val size: Int
) : File
