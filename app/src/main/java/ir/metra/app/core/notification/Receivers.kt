package ir.metra.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import ir.metra.app.core.date.JalaliCalendar
import ir.metra.app.data.preferences.UserPreferencesRepository
import ir.metra.app.domain.repository.WorkRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires at the configured time and notifies only when today has no work record.
 *
 * Re-schedules itself for the next day so the reminder survives reboots and the
 * inexactness of repeating alarms.
 */
@AndroidEntryPoint
class DailyReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: UserPreferencesRepository

    @Inject lateinit var workRecordRepository: WorkRecordRepository

    @Inject lateinit var notifier: ReminderNotifier

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return
        // Keep the process alive while the DB check and notification run.
        val pendingResult = goAsync()
        // The job is cancelled in `finally`: a scope created here outlives
        // onReceive otherwise, and leaking one per alarm adds up.
        val job = SupervisorJob()
        CoroutineScope(job + Dispatchers.Default).launch {
            try {
                val prefs = preferences.preferences.first()
                if (prefs.reminderEnabled) {
                    val today = JalaliCalendar.today().toEpochDay()
                    val alreadyLogged = workRecordRepository.hasRecordOn(today)
                    val alreadyNotified = prefs.lastReminderNotifiedEpochDay == today
                    if (!alreadyLogged && !alreadyNotified) {
                        if (notifier.notifyMissingWorkRecord()) {
                            preferences.markReminderNotified(today)
                        }
                    }
                    scheduler.schedule(prefs.reminderMinuteOfDay)
                }
            } finally {
                job.cancel()
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "ir.metra.app.action.CHECK_DAILY_RECORD"
    }
}

/** Re-arms the reminder after a reboot or an app update. */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: UserPreferencesRepository

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        // The job is cancelled in `finally`: a scope created here outlives
        // onReceive otherwise, and leaking one per alarm adds up.
        val job = SupervisorJob()
        CoroutineScope(job + Dispatchers.Default).launch {
            try {
                val prefs = preferences.preferences.first()
                if (prefs.reminderEnabled) {
                    scheduler.schedule(prefs.reminderMinuteOfDay)
                }
            } finally {
                job.cancel()
                pendingResult.finish()
            }
        }
    }
}
