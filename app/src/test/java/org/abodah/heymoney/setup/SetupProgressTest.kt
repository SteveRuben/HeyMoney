package org.abodah.heymoney.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProgressTest {

    private val today = 20_000L

    @Test
    fun `nothing granted - both permissions to do, services unavailable`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = false,
            notificationAccessGranted = false,
        )

        assertEquals(5, progress.totalCount)
        assertEquals(0, progress.doneCount)
        assertEquals(2, progress.actionableCount)
        assertEquals(2, progress.remainingCount)
        assertEquals(0, progress.percentComplete)
        assertFalse(progress.isComplete)
        assertEquals(SetupItemState.TODO, progress.stateOf(SetupItemId.SMS_PERMISSION))
        assertEquals(
            SetupItemState.TODO,
            progress.stateOf(SetupItemId.NOTIFICATION_ACCESS)
        )
    }

    @Test
    fun `future services are honestly unavailable, never to do or done`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = true,
            notificationAccessGranted = true,
        )

        assertEquals(
            SetupItemState.UNAVAILABLE,
            progress.stateOf(SetupItemId.BANK_CONNECTION)
        )
        assertEquals(SetupItemState.UNAVAILABLE, progress.stateOf(SetupItemId.EMAIL_SCAN))
        assertEquals(SetupItemState.UNAVAILABLE, progress.stateOf(SetupItemId.STORED_CARD))
    }

    @Test
    fun `one permission granted - half way there`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = true,
            notificationAccessGranted = false,
        )

        assertEquals(1, progress.doneCount)
        assertEquals(1, progress.remainingCount)
        assertEquals(50, progress.percentComplete)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `both permissions granted - setup complete despite future services`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = true,
            notificationAccessGranted = true,
        )

        assertEquals(2, progress.doneCount)
        assertEquals(0, progress.remainingCount)
        assertEquals(100, progress.percentComplete)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `reminder due when steps remain and none shown yet`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = false,
            notificationAccessGranted = true,
        )

        assertTrue(SetupProgressCalculator.shouldRemind(progress, null, today))
    }

    @Test
    fun `no second reminder the same day`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = false,
            notificationAccessGranted = true,
        )

        assertFalse(SetupProgressCalculator.shouldRemind(progress, today, today))
    }

    @Test
    fun `reminder due again the next day while steps remain`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = true,
            notificationAccessGranted = false,
        )

        assertTrue(SetupProgressCalculator.shouldRemind(progress, today - 1, today))
    }

    @Test
    fun `no reminder once setup is complete, ever`() {
        val progress = SetupProgressCalculator.progress(
            smsGranted = true,
            notificationAccessGranted = true,
        )

        assertFalse(SetupProgressCalculator.shouldRemind(progress, null, today))
        assertFalse(SetupProgressCalculator.shouldRemind(progress, today - 30, today))
    }
}
