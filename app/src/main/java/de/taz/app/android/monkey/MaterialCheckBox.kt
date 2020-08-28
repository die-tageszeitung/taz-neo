package de.taz.app.android.monkey

import com.google.android.material.checkbox.MaterialCheckBox

fun MaterialCheckBox.markRequired() {
    text = "$text *"
}