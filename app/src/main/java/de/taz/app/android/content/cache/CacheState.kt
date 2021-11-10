package de.taz.app.android.content.cache

/**
 * This enum describes the different states a cache of a certain item can be in.
 * METADATA being the database structure of a cachable item (i.e. [de.taz.app.android.api.models.Issue], [de.taz.app.android.api.models.Article] etc.)
 * CONTENT being the files belonging to that item
 */
enum class CacheState {
    ABSENT,
    LOADING_METADATA,
    METADATA_PRESENT,
    LOADING_CONTENT,
    DELETING_CONTENT,
    DELETING_METADATA,
    PRESENT
}