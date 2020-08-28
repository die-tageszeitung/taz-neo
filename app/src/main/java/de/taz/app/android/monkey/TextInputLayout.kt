package de.taz.app.android.monkey

import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.markRequired() {
    hint = "$hint *"
}

fun TextInputLayout.setError(@StringRes stringRes: Int) {
    error = context.resources.getString(stringRes)
}