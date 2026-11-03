package org.abodah.heymoney.alerts

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import org.abodah.heymoney.messages.MessageSubscriptionMatcher
import org.abodah.heymoney.messages.RawMessage
import javax.inject.Inject

/** Turns incoming bank SMS into pending drafts. Needs the RECEIVE_SMS permission. */
@AndroidEntryPoint
class SmsAlertReceiver : BroadcastReceiver() {

    @Inject
    lateinit var store: AlertStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // multipart SMS arrive as several parts from the same sender
        messages.groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, parts) ->
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            if (!BankAlertParser.looksLikeBankAlert(body)) return@forEach
            BankAlertParser.parse(BankAlert.Source.SMS, sender, body)?.let(store::add)
        }
    }
}

/** Turns bank-app notifications into pending drafts. The user grants access in system settings. */
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var store: AlertStore

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        // Mail apps: metadata only — the sender and the subject line, never
        // the body (EXTRA_BIG_TEXT can carry a snippet of it, so it is not
        // read on this path). A subject that reads like a subscription
        // receipt or a bill becomes a pending alert the user can confirm,
        // like a bank alert; nothing is tracked silently.
        if (sbn.packageName in EMAIL_PACKAGES) {
            handleEmailNotification(sbn.packageName, title, extras)
            return
        }

        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val body = "$title $text".trim()
        if (!BankAlertParser.looksLikeBankAlert(body)) return
        BankAlertParser.parse(BankAlert.Source.NOTIFICATION, title.ifBlank { sbn.packageName }, body)
            ?.let(store::add)
    }

    private fun handleEmailNotification(
        packageName: String,
        title: String,
        extras: android.os.Bundle,
    ) {
        val subject = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (subject.isBlank()) return
        val sender = title.ifBlank { packageName }
        val found = MessageSubscriptionMatcher.chargeIn(
            RawMessage(
                sender = sender,
                body = "$sender $subject",
                dateMillis = System.currentTimeMillis(),
            )
        ) ?: return
        val amount = found.amount ?: return
        store.add(
            BankAlert(
                id = BankAlert.idFor(BankAlert.Source.EMAIL, "$sender|$subject"),
                source = BankAlert.Source.EMAIL,
                sender = sender,
                raw = "$sender — $subject".take(400),
                amount = amount,
                currency = found.currency,
                isIncome = false,
                merchant = found.serviceName,
                date = System.currentTimeMillis(),
            )
        )
    }

    private companion object {
        /** Mail apps whose notifications are read for subscription receipts. */
        val EMAIL_PACKAGES = setOf(
            "com.google.android.gm", // Gmail
            "com.microsoft.office.outlook", // Outlook
            "com.yahoo.mobile.client.android.mail", // Yahoo Mail
        )
    }
}
