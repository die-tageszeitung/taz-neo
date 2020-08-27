package de.taz.app.android.util

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.taz.app.android.R

class AppTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(text: Editable?) {
                text?.let {
                    (parent.parent as? TextInputLayout)?.let { textInputLayout ->
                        if (textInputLayout.counterMaxLength > 0
                            && text.length > textInputLayout.counterMaxLength
                        ) {
                            val counterMaxLength = textInputLayout.counterMaxLength
                            removeTextChangedListener(this)
                            setText(text.substring(0, counterMaxLength))
                            setSelection(counterMaxLength)
                            addTextChangedListener(this)
                        }
                        if (textInputLayout.error != null) {
                            textInputLayout.error = null
                        }
                    }
                }
            }
        })
    }

}