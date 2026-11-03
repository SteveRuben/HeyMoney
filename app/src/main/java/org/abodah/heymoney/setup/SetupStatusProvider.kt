package org.abodah.heymoney.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the real, current state of the two permissions HeyMoney setup
 * depends on — the same checks the Bank alerts screen performs. Nothing is
 * cached: every call asks the OS again, so a permission granted or revoked
 * in the system settings is seen the next time the setup is displayed.
 */
@Singleton
class SetupStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun smsGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun notificationAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun progress(): SetupProgress = SetupProgressCalculator.progress(
        smsGranted = smsGranted(),
        notificationAccessGranted = notificationAccessGranted(),
    )
}
