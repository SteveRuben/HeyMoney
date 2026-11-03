package org.abodah.heymoney.setup

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.domain.AppStarter
import com.ivy.ui.R
import com.ivy.wallet.RootViewModel
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last day the setup reminder was shown, so the daily worker
 * can guarantee at most one reminder per day even if it runs twice.
 * SharedPreferences, same storage style as the other HeyMoney stores.
 */
@Singleton
class SetupReminderStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs =
        context.getSharedPreferences("heymoney_setup_reminder", Context.MODE_PRIVATE)

    fun lastReminderEpochDay(): Long? =
        prefs.getLong(KEY_LAST_REMINDER_EPOCH_DAY, NO_REMINDER_YET)
            .takeIf { it != NO_REMINDER_YET }

    fun markReminded(epochDay: Long) {
        prefs.edit().putLong(KEY_LAST_REMINDER_EPOCH_DAY, epochDay).apply()
    }

    private companion object {
        const val KEY_LAST_REMINDER_EPOCH_DAY = "last_reminder_epoch_day"
        const val NO_REMINDER_YET = -1L
    }
}

/**
 * Schedules the daily setup check. Same shape as the subscription renewal
 * scheduler: a unique periodic WorkManager job, kept if already scheduled,
 * aimed at the morning so the reminder arrives during the day.
 */
@Singleton
class SetupReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val now = LocalDateTime.now()
        var firstRun = now.toLocalDate().atTime(9, 0)
        if (!firstRun.isAfter(now)) firstRun = firstRun.plusDays(1)
        val initialDelay = ChronoUnit.SECONDS.between(now, firstRun)

        val request = PeriodicWorkRequestBuilder<SetupReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "setup_reminder_daily"
    }
}

/**
 * Daily check: while setup steps the user can still act on remain, post one
 * local notification naming how many are left — at most once a day, and
 * never again once the setup is complete. If the app may not post
 * notifications (permission not granted or notifications off), the check
 * quietly does nothing: the reminder never forces its way in.
 * Tapping the notification opens the Configuration screen.
 */
@HiltWorker
class SetupReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val statusProvider: SetupStatusProvider,
    private val store: SetupReminderStore,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val todayEpochDay = LocalDate.now().toEpochDay()
        val progress = statusProvider.progress()
        if (!SetupProgressCalculator.shouldRemind(
                progress = progress,
                lastReminderEpochDay = store.lastReminderEpochDay(),
                todayEpochDay = todayEpochDay,
            )
        ) {
            return@withContext Result.success()
        }
        if (!canPostNotifications()) return@withContext Result.success()

        notify(progress.remainingCount)
        store.markReminded(todayEpochDay)
        Result.success()
    }

    private fun canPostNotifications(): Boolean {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notify(remainingCount: Int) {
        val context = applicationContext
        val openSetupIntent = appStarter.getRootIntent().apply {
            // A distinct action keeps this PendingIntent apart from the
            // launcher one, because Intent.filterEquals() ignores extras.
            action = ACTION_OPEN_SETUP
            putExtra(RootViewModel.EXTRA_OPEN_SETUP, true)
        }
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.SETUP_REMINDER,
                priority = NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentTitle(context.getString(R.string.setup_reminder_title))
            .setContentText(
                if (remainingCount == 1) {
                    context.getString(R.string.setup_reminder_text_one)
                } else {
                    context.getString(
                        R.string.setup_reminder_text_many,
                        remainingCount
                    )
                }
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    openSetupIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )
            )
        notificationService.showNotification(notification, NOTIFICATION_ID)
    }

    private companion object {
        const val ACTION_OPEN_SETUP = "org.abodah.heymoney.ACTION_OPEN_SETUP"
        const val NOTIFICATION_ID = 917_311
    }
}
