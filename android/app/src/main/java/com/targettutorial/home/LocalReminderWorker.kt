package com.targettutorial.home

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Local on-device reminder only. It is not a remote push notification.
 *
 * Required Gradle dependencies:
 * implementation 'androidx.work:work-runtime-ktx:2.10.0'
 * implementation 'androidx.core:core-ktx:1.15.0'
 *
 * Android 13+: request POST_NOTIFICATIONS permission from an Activity before
 * scheduling reminders; this Worker safely skips notification if permission is denied.
 */
class LocalReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Target Tutorial Home"
        val message = inputData.getString(KEY_MESSAGE) ?: "তোমার পড়াশোনার Reminder"
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, System.currentTimeMillis().toInt())

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Study Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        return runCatching {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val CHANNEL_ID = "target_tutorial_study_reminders"
        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_NOTIFICATION_ID = "notification_id"

        fun schedule(
            context: Context,
            title: String,
            message: String,
            delay: Long,
            unit: TimeUnit = TimeUnit.MINUTES,
            notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        ) {
            val data = Data.Builder()
                .putString(KEY_TITLE, title)
                .putString(KEY_MESSAGE, message)
                .putInt(KEY_NOTIFICATION_ID, notificationId)
                .build()

            val request = OneTimeWorkRequestBuilder<LocalReminderWorker>()
                .setInitialDelay(delay.coerceAtLeast(0), unit)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
