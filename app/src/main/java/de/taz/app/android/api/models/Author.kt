package de.taz.app.android.api.models

import de.taz.app.android.persistence.repository.DownloadRepository

data class Author (
   val name: String? = null,
   val imageAuthor: FileEntry? = null
) {
   fun isDownloaded(): Boolean {
      DownloadRepository.getInstance().let {
         return it.isDownloaded(imageAuthor?.name)
      }
   }
}