package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.Downloadable

data class Author (
   val name: String? = null,
   val imageAuthor: FileEntry? = null
) : Downloadable {

   override fun getAllFileNames(): List<String> {
      return imageAuthor?.name?.let { listOf(it) } ?: listOf()
   }

}