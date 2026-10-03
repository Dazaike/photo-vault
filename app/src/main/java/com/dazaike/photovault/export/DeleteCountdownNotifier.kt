package com.dazaike.photovault.export

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dazaike.photovault.R

/**
 * Ongoing notification whose chronometer counts down to an auto-delete, with a "Delete now" action. On
 * Android 16+ it is requested as a promoted Live Update (status-bar chip with the countdown); older versions
 * get a plain ongoing notification. The countdown is drawn by the system, so it keeps ticking with the app
 * process dead, and the timeout removes the notification at expiry.
 */
object DeleteCountdownNotifier {
    private const val CHANNEL_ID = "auto_delete_countdown"
    const val ACTION_DELETE_NOW = "com.dazaike.photovault.action.DELETE_NOW"
    const val EXTRA_EXPIRY = "expiry"

    /** One notification per scheduled batch; a batch is identified by its expiry time. */
    fun notificationId(expiryEpochMs: Long): Int = (expiryEpochMs / 1000).toInt()

    fun cancel(context: Context, expiryEpochMs: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(expiryEpochMs))
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context, itemCount: Int, expiryEpochMs: Long) {
        if (!canPost(context)) return
        val delayMs = expiryEpochMs - System.currentTimeMillis()
        if (delayMs <= 0) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Auto-delete countdown", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Time left before exported photos are deleted from storage"
                setShowBadge(false)
            },
        )
        val noun = if (itemCount == 1) "item" else "items"
        val title = "Deleting $itemCount exported $noun"
        val text = "Removed from storage when the timer ends"
        val id = notificationId(expiryEpochMs)
        val deleteNow = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, AutoDeleteReceiver::class.java)
                .setAction(ACTION_DELETE_NOW)
                .putExtra(EXTRA_EXPIRY, expiryEpochMs),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(expiryEpochMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setTimeoutAfter(delayMs)
                .addAction(
                    Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_trash), "Delete now", deleteNow)
                        .build(),
                )
                .setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
                .setRequestPromotedOngoing(true)
                .build()
        } else {
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(expiryEpochMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setTimeoutAfter(delayMs)
                .addAction(R.drawable.ic_trash, "Delete now", deleteNow)
                .build()
        }
        manager.notify(id, notification)
    }
}
