package de.taz.app.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.IssueRepository
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
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        val tracker = Tracker.getInstance(context.applicationContext)
        tracker.trackWidgetEnabledEvent()
    }

    override fun onDisabled(context: Context) {
        val tracker = Tracker.getInstance(context.applicationContext)
        tracker.trackWidgetDisabledEvent()
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {

    val storageService = StorageService.getInstance(context.applicationContext)
    val issueRepository = IssueRepository.getInstance(context.applicationContext)
    val momentRepository = MomentRepository.getInstance(context.applicationContext)

    // Construct the RemoteViews object
    CoroutineScope(Dispatchers.Default).launch {

        val issueStub = issueRepository.getLatestIssueStub()

        if (issueStub == null) return@launch
        val momentImage = momentRepository.get(issueStub)?.getMomentImage()

        if (momentImage != null) {
            storageService.getAbsolutePath(momentImage)?.let {
                if (File(it).exists()) {
                    val bitmapOptions = BitmapFactory.Options()

                    val myBitmap = BitmapFactory.decodeFile(it, bitmapOptions)

                    val views = RemoteViews(context.packageName, R.layout.moment_widget)
                    views.setImageViewBitmap(R.id.widget_cover_view, myBitmap)

                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, SplashActivity::class.java),
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    views.setOnClickPendingIntent(
                        R.id.widget_cover_view,
                        pendingIntent
                    )

                    // Instruct the widget manager to update the widget
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}