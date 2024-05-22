package de.taz.app.android.util

import android.app.Activity
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

fun View.hideSoftInputKeyboard() {
    val inputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
    if (windowToken != null && inputMethodManager != null) {
        inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }
}

fun Activity.hideSoftInputKeyboard() {
    // Any view contains the windowToken. We can simply take it from the windows decor "root" view.
    window.peekDecorView()?.hideSoftInputKeyboard()
}

fun Fragment.hideSoftInputKeyboard() {
    view?.hideSoftInputKeyboard()
}
