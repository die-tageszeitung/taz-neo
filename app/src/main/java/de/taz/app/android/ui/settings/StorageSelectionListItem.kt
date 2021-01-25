package de.taz.app.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import de.taz.app.android.R
import kotlinx.android.synthetic.main.listitem_select_storage.view.*

class StorageSelectionListItem @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyle: Int = 0
) : ConstraintLayout(context, attributeSet, defStyle), Checkable {
    init {
        this.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        inflate(context, R.layout.listitem_select_storage, this)
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