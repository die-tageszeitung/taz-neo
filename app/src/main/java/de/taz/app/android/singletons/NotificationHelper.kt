package de.taz.app.android.singletons

import android.app.PendingIntent
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.taz.app.android.R
import de.taz.app.android.util.SingletonHolder

class NotificationHelper private constructor(private val applicationContext: Context) {

    private val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    companion object : SingletonHolder<NotificationHelper, Context>(::NotificationHelper)

    fun showNotification(
        @StringRes title: Int,
        @StringRes body: Int,
        channelId: String,
        bigText: Boolean = false,
        pendingIntent: PendingIntent? = null,
        notificationId: Int = 0
    ) {
        showNotification(
            applicationContext.getString(title),
            applicationContext.getString(body),
            channelId, bigText, pendingIntent, notificationId
        )
    }


    fun showNotification(
        title: String,
        body: String,
        channelId: String,
        bigText: Boolean = false,
        pendingIntent: PendingIntent? = null,
        notificationId: Int = 0
    ) {

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (bigText) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
        }

        pendingIntent?.let {
            builder.setContentIntent(pendingIntent)
            builder.setAutoCancel(true)
        }

        notificationManagerCompat.notify(notificationId, builder.build())
    }

}