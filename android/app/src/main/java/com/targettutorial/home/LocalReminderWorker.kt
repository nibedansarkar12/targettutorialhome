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

/** Local on-device study reminder. This is not remote push notification. */
class LocalReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val title = inputData.getString(KEY_TITLE) ?: "Target Tutorial Home"
        val message = inputData.getString(KEY_MESSAGE) ?: "তোমার পড়াশোনার Reminder"
        val notificationId = inputData.getInt(
            KEY_NOTIFICATION_ID,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return androidx.work.ListenableWorker.Result.success()
        }

        return try {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Study Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
            androidx.work.ListenableWorker.Result.success()
        } catch (_: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
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
                .setInitialDelay(delay.coerceAtLeast(0L), unit)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
