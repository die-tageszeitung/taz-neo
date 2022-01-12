package de.taz.app.android.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.dataStore.StorageDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.pow


class StorageSelectionDialog(
    val context: Context
) {
    private val storageDataStore = StorageDataStore.getInstance(context.applicationContext)

    val options = mapOf(
        StorageLocation.INTERNAL to context.getString(R.string.settings_storage_type_internal),
        StorageLocation.EXTERNAL to context.getString(R.string.settings_storage_type_external)
    )

    private val listAdapter = object :
        ArrayAdapter<StorageLocation>(context, R.layout.item_select_with_info, R.id.itemText) {

        init {
            options.forEach { add(it.key) }
        }

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return super.getView(position, convertView, parent).apply {

                val textView = this.findViewById<TextView>(R.id.itemText)
                val radioView = this.findViewById<RadioButton>(R.id.itemCheckbox)
                val itemIcon = this.findViewById<ImageView>(R.id.itemIcon)
                textView?.text = options[getItem(position)]
                itemIcon?.visibility = View.GONE
                if (!isEnabled(position)) {
                    textView?.setTextColor(ContextCompat.getColor(context, R.color.textColorAccent))
                    radioView?.isEnabled = false
                } else {
                    radioView?.isEnabled = true
                    textView?.setTextColor(ContextCompat.getColor(context, R.color.textColor))
                }
                when (getItem(position)) {
                    StorageLocation.EXTERNAL -> {
                        if (isEnabled(position)) {
                            val externalFreeBytes = context.getExternalFilesDir(null)
                                ?.let { StatFs(it.path).availableBytes } ?: 0
                            textView?.text = "${options[getItem(position)]}\n(${
                                if (Environment.isExternalStorageRemovable()) "SD-Karte" else "Telefon"
                            }, %.2fGiB frei)".format(externalFreeBytes / 1024.0.pow(3.0))
                        } else {
                            itemIcon?.visibility = View.VISIBLE
                            itemIcon.setOnClickListener {
                                AlertDialog.Builder(context)
                                    .setMessage(R.string.settings_storage_external_unavailable_hint)
                                    .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                                    .show()

                            }
                        }
                    }
                    StorageLocation.INTERNAL -> {
                        val internalFreeBytes = StatFs(context.filesDir.path).availableBytes
                        textView?.text = "${textView?.text}\n(%.2fGiB frei)".format(
                            internalFreeBytes / 1024.0.pow(3.0)
                        )
                    }
                    else -> Unit
                }
            }
        }

        override fun isEnabled(position: Int): Boolean {
            return if (
                getItem(position) == StorageLocation.EXTERNAL
            ) {
                val externalStorageAvailable =
                    Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
                val externalStorageEmulated = Environment.isExternalStorageEmulated()
                val externalStorageRemovable = Environment.isExternalStorageRemovable()
                externalStorageAvailable && (!externalStorageEmulated || externalStorageRemovable)
            } else true
        }
    }

    fun show() {
        CoroutineScope(Dispatchers.Main).launch {
            val currentLocation = storageDataStore.storageLocation.get()
            AlertDialog.Builder(context)
                .setNegativeButton(R.string.cancel_button) { dialog, _ -> dialog.dismiss() }
                .setSingleChoiceItems(
                    listAdapter, listAdapter.getPosition(currentLocation)
                ) { dialog, which ->
                    CoroutineScope(Dispatchers.Main).launch {
                        listAdapter.getItem(which)?.let { storageDataStore.storageLocation.set(it) }
                        dialog.dismiss()
                    }
                }.show()

        }
    }
}