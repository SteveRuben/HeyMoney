package org.abodah.heymoney.setup

/** One thing the user may still have to set up for HeyMoney to work fully. */
enum class SetupItemId {
    SMS_PERMISSION,
    NOTIFICATION_ACCESS,
    BANK_CONNECTION,
    EMAIL_SCAN,
    STORED_CARD,
}

/**
 * Where one setup item stands. [UNAVAILABLE] means the feature cannot be
 * switched on at all yet — it needs an external service or a HeyMoney server
 * that does not exist — so it is shown honestly instead of pretending a
 * connection flow exists.
 */
enum class SetupItemState {
    DONE,
    TODO,
    UNAVAILABLE,
}

data class SetupItemProgress(
    val id: SetupItemId,
    val state: SetupItemState,
)

/**
 * The whole setup picture. Progress is measured on the items the user can
 * actually act on (the two permissions): the unavailable services are listed
 * for transparency but cannot make progress stall below 100 % forever, and
 * they are not counted as steps "remaining" for the user. As soon as one of
 * them ships, its state flips to [SetupItemState.TODO] and it joins the
 * count like the others.
 */
data class SetupProgress(
    val items: List<SetupItemProgress>,
) {
    /** Every item shown on the screen, services included. */
    val totalCount: Int get() = items.size

    val doneCount: Int get() = items.count { it.state == SetupItemState.DONE }

    /** Items the user can act on today: done + still to do. */
    val actionableCount: Int
        get() = items.count { it.state != SetupItemState.UNAVAILABLE }

    /** Actionable items still waiting on the user. */
    val remainingCount: Int get() = items.count { it.state == SetupItemState.TODO }

    /** 100 when everything the user can set up is set up. */
    val percentComplete: Int
        get() = if (actionableCount == 0) {
            100
        } else {
            doneCount * 100 / actionableCount
        }

    val isComplete: Boolean get() = remainingCount == 0

    fun stateOf(id: SetupItemId): SetupItemState =
        items.first { it.id == id }.state
}

/** Pure setup maths: which state each item is in, and when to remind. */
object SetupProgressCalculator {

    fun progress(
        smsGranted: Boolean,
        notificationAccessGranted: Boolean,
    ): SetupProgress = SetupProgress(
        items = listOf(
            SetupItemProgress(
                id = SetupItemId.SMS_PERMISSION,
                state = if (smsGranted) SetupItemState.DONE else SetupItemState.TODO,
            ),
            SetupItemProgress(
                id = SetupItemId.NOTIFICATION_ACCESS,
                state = if (notificationAccessGranted) {
                    SetupItemState.DONE
                } else {
                    SetupItemState.TODO
                },
            ),
            // No banking-data provider account and no HeyMoney server exist
            // yet, so none of these can honestly be offered as working.
            SetupItemProgress(SetupItemId.BANK_CONNECTION, SetupItemState.UNAVAILABLE),
            SetupItemProgress(SetupItemId.EMAIL_SCAN, SetupItemState.UNAVAILABLE),
            SetupItemProgress(SetupItemId.STORED_CARD, SetupItemState.UNAVAILABLE),
        )
    )

    /**
     * A reminder is due only while the user still has something they can do,
     * and at most once per day: [lastReminderEpochDay] is the day a reminder
     * was last shown, so anything earlier than [todayEpochDay] may remind
     * again, the same day may not.
     */
    fun shouldRemind(
        progress: SetupProgress,
        lastReminderEpochDay: Long?,
        todayEpochDay: Long,
    ): Boolean =
        !progress.isComplete &&
            (lastReminderEpochDay == null || lastReminderEpochDay < todayEpochDay)
}
