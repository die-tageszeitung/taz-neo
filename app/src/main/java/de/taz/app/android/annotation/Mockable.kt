package de.taz.app.android.annotation

/**
 * Classes needed to be mocked from the androidTests (NOT unit tests) have to be marked
 * with @Mockable to be opened during compile time.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Mockable