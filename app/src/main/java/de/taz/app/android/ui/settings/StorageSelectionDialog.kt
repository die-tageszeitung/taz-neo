package de.taz.app.android.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.util.getStorageLocationCaption


class StorageSelectionDialog(val context: Context, private val settingsViewModel: SettingsViewModel) {
    val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)
    val options = mapOf(
        context.getString(R.string.settings_storage_type_internal) to StorageLocation.INTERNAL,
        context.getString(R.string.settings_storage_type_external) to StorageLocation.EXTERNAL
    )

    private val listAdapter = object: ArrayAdapter<String>(context, android.R.layout.select_dialog_singlechoice) {
        init {
            options.forEach { add(it.key) }
        }

        override fun areAllItemsEnabled(): Boolean {
            return false
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return super.getView(position, convertView, parent).apply {
                if (!isEnabled(position)) {
                    (this as? CheckedTextView)?.setTextColor(ContextCompat.getColor(context, R.color.textColorAccent))
                } else {
                    (this as? CheckedTextView)?.setTextColor(ContextCompat.getColor(context, R.color.textColor))
                }
            }
        }

        override fun isEnabled(position: Int): Boolean {
            return if (
                options[getItem(position)] == StorageLocation.EXTERNAL
            ) {
                val externalStorageAvailable = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
                val externalStorageRemovable = Environment.isExternalStorageRemovable()
                externalStorageAvailable && externalStorageRemovable
            } else true
        }
    }

    fun show() {
        val currentLocation = settingsViewModel.storageLocationLiveData.value
        AlertDialog.Builder(context)
            .setNegativeButton(R.string.cancel_button) { dialog, _ -> dialog.dismiss() }
            .setSingleChoiceItems(
                listAdapter, listAdapter.getPosition(
                    context.getStorageLocationCaption(
                        currentLocation
                    )
                )
            ) { dialog, which ->
                settingsViewModel.storageLocationLiveData.postValue(
                        options[listAdapter.getItem(which)] ?: StorageLocation.INTERNAL
                )

                dialog.dismiss()
            }.show()

    }
}