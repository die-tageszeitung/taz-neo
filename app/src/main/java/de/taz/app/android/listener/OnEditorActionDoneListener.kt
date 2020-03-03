package de.taz.app.android.listener

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView

class OnEditorActionDoneListener(
    private val onActionDoneFunction: () -> Unit
) : TextView.OnEditorActionListener {
    override fun onEditorAction(
        v: TextView?,
        actionId: Int,
        event: KeyEvent?
    ): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            onActionDoneFunction()
            return true
        }
        return false
    }
}
