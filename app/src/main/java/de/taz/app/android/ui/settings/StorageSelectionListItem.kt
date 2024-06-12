package de.taz.app.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import android.widget.RadioButton
import androidx.constraintlayout.widget.ConstraintLayout
import de.taz.app.android.R

class StorageSelectionListItem @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : ConstraintLayout(context, attributeSet), Checkable {
    private val itemCheckbox: RadioButton
    init {
        this.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        val view = inflate(context, R.layout.listitem_select_storage, this)
        itemCheckbox = view.findViewById(R.id.itemCheckbox)
    }

    override fun setChecked(checked: Boolean) {
        itemCheckbox.isChecked = checked
    }

    override fun isChecked(): Boolean {
        return itemCheckbox.isChecked
    }

    override fun toggle() {
        itemCheckbox.isChecked = !itemCheckbox.isChecked
    }

}