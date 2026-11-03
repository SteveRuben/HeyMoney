package org.abodah.heymoney.setup

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * State of the Configuration screen. The progress is re-read from the OS on
 * every [refresh] — the screen calls it when it comes back to the foreground,
 * because both permissions are granted (or revoked) outside the app, in the
 * system dialogs and settings.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val statusProvider: SetupStatusProvider,
    reminderScheduler: SetupReminderScheduler,
) : ViewModel() {

    private val _progress = MutableStateFlow(statusProvider.progress())
    val progress: StateFlow<SetupProgress> = _progress

    init {
        // Opening the setup once arms the daily reminder; the work is
        // unique, so scheduling again is a no-op (same pattern as the
        // subscription renewal reminders).
        reminderScheduler.schedule()
    }

    fun refresh() {
        _progress.value = statusProvider.progress()
    }
}
