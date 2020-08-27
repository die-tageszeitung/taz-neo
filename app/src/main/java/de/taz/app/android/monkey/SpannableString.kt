package de.taz.app.android.monkey

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import kotlin.reflect.KFunction0

fun SpannableString.onClick(text: String, function: KFunction0<Unit>) {
    val indexOf = indexOf(text)
    if (indexOf >= 0) {
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                function()
            }
        }, indexOf, indexOf + text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

