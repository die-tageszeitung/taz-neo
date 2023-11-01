package de.taz.app.android.api.models

data class Audio(
    val file: FileEntry,
    val playtime: Int?,
    val duration: Float?,
    val speaker: AudioSpeaker,
    /**
     * Optional list of breaks for the [Audio] item.
     * Each break is given in seconds with fractions.
     */
    val breaks: List<Float>?
) {

    // Workaround to prevent from comparisons on Issues (and Articles) becoming too expensive.
    // An Issue has multiple Articles, which in turn have Audios with a list of multiple breaks.
    // To determine if two Issues are equal, all of these have to be compared.
    // We can relax the Audio equality by only looking at the referenced file.
    override fun equals(other: Any?): Boolean {
        val otherAudio = (other as? Audio)
        return file == otherAudio?.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }
}