package de.taz.app.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.splash.SplashActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Implementation of App Widget functionality showing the latest moment.
 */
class MomentWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val storageService = StorageService.getInstance(context.applicationContext)
        val momentRepository = MomentRepository.getInstance(context.applicationContext)

        val pendingResult = goAsync()

        // Perform data fetching and bitmap decoding once for all widget instances
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val momentStub = momentRepository.getRecent() ?: return@launch
                val moment = momentRepository.momentStubToMoment(momentStub)
                val momentImage = moment.getMomentImage()
                val imagePath = momentImage?.let { storageService.getAbsolutePath(it) }

                val coverBitmap = if (imagePath != null && File(imagePath).exists()) {
                    decodeSampledBitmapFromFile(imagePath, 400, 400)
                } else {
                    null
                }

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, coverBitmap)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        Tracker.getInstance(context.applicationContext).trackWidgetEnabledEvent()
    }

    override fun onDisabled(context: Context) {
        Tracker.getInstance(context.applicationContext).trackWidgetDisabledEvent()
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    coverBitmap: Bitmap? = null
) {
    val views = RemoteViews(context.packageName, R.layout.moment_widget)

    if (coverBitmap != null) {
        views.setImageViewBitmap(R.id.widget_cover_view, coverBitmap)
    } else {
        // Fallback to placeholder if no image is available
        views.setImageViewResource(R.id.widget_cover_view, R.drawable.moment_example)
    }

    // Using the system's official launch intent ensures the splash screen icon is shown
    // correctly during a cold start, matching the launcher behavior and animations.
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
        // Ensure we switch to the existing task and return to the Home screen
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(SplashActivity.KEY_SHOW_HOME, true)
    }

                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(
                            context,
                            SplashActivity::class.java
                        ).putExtra(SplashActivity.KEY_SHOW_HOME, true),
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

    views.setOnClickPendingIntent(
        R.id.widget_cover_view,
        pendingIntent
    )

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

/**
 * Decodes a bitmap from a file with downsampling to avoid memory issues and TransactionTooLargeException.
 */
private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        BitmapFactory.decodeFile(path, options)
    } catch (_: Exception) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
