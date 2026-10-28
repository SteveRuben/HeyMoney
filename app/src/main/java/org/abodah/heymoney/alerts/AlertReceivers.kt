package org.abodah.heymoney.alerts

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
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
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val body = "$title $text".trim()
        if (!BankAlertParser.looksLikeBankAlert(body)) return
        BankAlertParser.parse(BankAlert.Source.NOTIFICATION, title.ifBlank { sbn.packageName }, body)
            ?.let(store::add)
    }
}
