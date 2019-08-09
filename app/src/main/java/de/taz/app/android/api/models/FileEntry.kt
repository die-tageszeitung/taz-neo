package de.taz.app.android.api.models

data class FileEntry(
    override val name: String,
    override val storageType: StorageType,
    override val moTime: String,
    override val sha256: String,
    override val size: Int
) : File
