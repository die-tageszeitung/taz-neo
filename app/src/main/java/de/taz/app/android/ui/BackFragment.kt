package de.taz.app.android.ui

interface BackFragment {

    /**
     * function will be called if in activity back button is pressed
     * @return true if Fragment handled back action false otherwise
     */
    fun onBackPressed(): Boolean

}