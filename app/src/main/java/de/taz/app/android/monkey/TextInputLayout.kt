package de.taz.app.android.monkey

import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.markRequired() {
    hint = "$hint *"
}