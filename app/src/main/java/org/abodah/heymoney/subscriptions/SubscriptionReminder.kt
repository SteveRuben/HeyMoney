package org.abodah.heymoney.subscriptions

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.domain.AppStarter
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily renewal check. Same shape as the existing transaction
 * reminder: a unique periodic WorkManager job, kept if already scheduled,
 * aimed at the morning so the reminder arrives during the day.
 */
@Singleton
class SubscriptionReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule() {
        val now = LocalDateTime.now()
        var firstRun = now.toLocalDate().atTime(9, 0)
        if (!firstRun.isAfter(now)) firstRun = firstRun.plusDays(1)
        val initialDelay = ChronoUnit.SECONDS.between(now, firstRun)

        val request = PeriodicWorkRequestBuilder<SubscriptionReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "subscription_renewal_reminder"
    }
}

/**
 * Daily check: re-runs the detection and, for every confirmed subscription
 * whose next due date is within the user's reminder window (and was not
 * already notified for that due date), posts one local notification.
 * Everything is computed on-device from the local database.
 */
@HiltWorker
class SubscriptionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val accountDao: AccountDao,
    private val detector: SubscriptionDetector,
    private val store: SubscriptionStore,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val subscriptions = detector.detect(
            transactions = transactionDao.findAll(),
            plannedRules = plannedPaymentRuleDao.findAll(),
            accountCurrencies = accountDao.findAll().associate { it.id to it.currency },
            today = today,
        )
        subscriptions.forEach { subscription ->
            val decision = store.decisionFor(subscription.key)
            if (decision.status != SubscriptionStatus.CONFIRMED) return@forEach
            if (decision.reminderDays <= 0) return@forEach
            val daysLeft = ChronoUnit.DAYS.between(today, subscription.nextDueDate)
            if (daysLeft < 0 || daysLeft > decision.reminderDays) return@forEach
            val dueEpochDay = subscription.nextDueDate.toEpochDay()
            if (decision.lastNotifiedDueEpochDay == dueEpochDay) return@forEach
            notify(subscription)
            store.markNotified(subscription.key, dueEpochDay)
        }
        Result.success()
    }

    private fun notify(subscription: DetectedSubscription) {
        val context = applicationContext
        val price = "%,.2f %s".format(
            Locale.getDefault(), subscription.amount, subscription.currency.orEmpty()
        ).trim()
        val date = subscription.nextDueDate.format(
            DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
        )
        val notificationId = subscription.key.hashCode()
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.SUBSCRIPTION_RENEWAL,
                priority = NotificationCompat.PRIORITY_HIGH
            )
            .setContentTitle(context.getString(R.string.subs_notification_title))
            .setContentText(
                context.getString(R.string.subs_notification_text, subscription.name, price, date)
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    appStarter.getRootIntent(),
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )
            )
        notificationService.showNotification(notification, notificationId)
    }
}
