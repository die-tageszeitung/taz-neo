package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.CacheableDownload

data class Author (
   val name: String? = null,
   val imageAuthor: FileEntry? = null
) : CacheableDownload {

   override fun getAllFiles(): List<FileEntry> {
      return imageAuthor?.let { listOf(it) } ?: emptyList()
   }

}