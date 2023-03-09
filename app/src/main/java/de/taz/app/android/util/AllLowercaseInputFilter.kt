package de.taz.app.android.util

import android.text.InputFilter
import android.text.Spanned
import com.google.android.material.textfield.TextInputEditText

fun TextInputEditText.addAllLowercaseFilter() {
    val prevFilters = this.filters
    for (filter in prevFilters) {
        if (filter is AllLowerCaseInputFilter) {
            // Do not add the AllLowerCaseFilter twice
            return
        }
    }

    val newFilters = arrayOfNulls<InputFilter>(prevFilters.size + 1)
    System.arraycopy(prevFilters, 0, newFilters, 0, prevFilters.size)
    newFilters[prevFilters.size] = AllLowerCaseInputFilter()

    this.filters = newFilters
}

/**
 * Transform all input characters to lowercase bevore showing to users
 */
class AllLowerCaseInputFilter : InputFilter {
    override fun filter(
        source: CharSequence, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int
    ): CharSequence? {
        return source.toString().lowercase()
    }

}
