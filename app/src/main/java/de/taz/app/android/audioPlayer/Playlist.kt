package de.taz.app.android.audioPlayer

data class Playlist(
    val currentItemIdx: Int,
    val items: List<AudioPlayerItem>,
) {
    companion object {
        val EMPTY = Playlist(-1, emptyList())
    }

    fun getCurrentItem(): AudioPlayerItem? = items.getOrNull(currentItemIdx)

    fun getNextItem(): AudioPlayerItem? = items.getOrNull(currentItemIdx + 1)

    fun isAtEnd(): Boolean = items.lastIndex == currentItemIdx || currentItemIdx < 0

    fun isEmpty(): Boolean = items.isEmpty()

    fun append(
        newItems: List<AudioPlayerItem>,
        setCurrentItemIdx: Boolean = false
    ): Playlist {
        return if (currentItemIdx < 0 || items.isEmpty()) {
            Playlist(0, newItems)
        } else {
            val newPlaylist = Playlist(currentItemIdx, items + newItems)
            if (setCurrentItemIdx) {
                newPlaylist.copy(currentItemIdx = currentItemIdx + 1)
            } else {
                newPlaylist
            }
        }
    }

    override fun toString(): String {
        return "${this::class.simpleName}($currentItemIdx / #${items.size})"
    }
}