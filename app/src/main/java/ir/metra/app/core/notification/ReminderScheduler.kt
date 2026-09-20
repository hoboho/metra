package ir.metra.app.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.metra.app.core.date.JalaliCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder.
 *
 * Uses an inexact alarm and **re-arms itself** from the receiver, because
 * `setRepeating` drifts away from the user's chosen time on modern Android.
 * Inexact is deliberate: see the note in [schedule] on Doze and battery cost.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /** Schedules the next reminder at [minuteOfDay], today or tomorrow. */
    fun schedule(minuteOfDay: Int) {
        val manager = alarmManager ?: return
        val triggerAt = nextTriggerMillis(minuteOfDay)
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Inexact on purpose. A "did you log today?" nudge does not need to fire
        // on the exact minute, and `setExactAndAllowWhileIdle` pulls the device
        // out of Doze, which is the single most expensive thing an app can do to
        // a battery. `set` lets the OS batch this alarm with everything else
        // already scheduled near that time, so it costs almost nothing.
        manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel() {
        val manager = alarmManager ?: return
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }


    /** Next occurrence of [minuteOfDay] in the device zone, never in the past. */
    fun nextTriggerMillis(minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60)
        var candidate = LocalDateTime.of(LocalDate.now(zone), time)
        if (!candidate.atZone(zone).toInstant().toEpochMilli().let { it > System.currentTimeMillis() }) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        private const val REQUEST_CODE = 3001
    }
}
