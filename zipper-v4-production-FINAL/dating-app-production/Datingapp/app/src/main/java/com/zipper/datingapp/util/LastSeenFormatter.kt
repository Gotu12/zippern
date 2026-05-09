package com.zipper.datingapp.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Human-readable "last active" strings for chat headers and profile UI. */
object LastSeenFormatter {

    fun format(lastSeenEpochMs: Long, nowMs: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
        if (lastSeenEpochMs <= 0L) return "Last seen a while ago"
        val delta = (nowMs - lastSeenEpochMs).coerceAtLeast(0L)
        when {
            delta < TimeUnit.MINUTES.toMillis(1) -> return "Last seen just now"
            delta < TimeUnit.HOURS.toMillis(24) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(delta)
                if (hours >= 1) {
                    return "Last seen ${hours} hr${if (hours > 1) "s" else ""} ago"
                }
                val mins = TimeUnit.MILLISECONDS.toMinutes(delta).coerceAtLeast(1)
                return "Last seen ${mins} min ago"
            }
        }
        val calNow = Calendar.getInstance(locale).apply { timeInMillis = nowMs }
        val calSeen = Calendar.getInstance(locale).apply { timeInMillis = lastSeenEpochMs }
        val timeFmt = SimpleDateFormat("h:mm a", locale)
        val sameDay = calNow.get(Calendar.YEAR) == calSeen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calSeen.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return "Last seen today at ${timeFmt.format(Date(lastSeenEpochMs))}"
        }
        val yesterday = (calNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == calSeen.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == calSeen.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "Last seen yesterday at ${timeFmt.format(Date(lastSeenEpochMs))}"
        }
        val df = SimpleDateFormat("MMM d 'at' h:mm a", locale)
        return "Last seen ${df.format(Date(lastSeenEpochMs))}"
    }
}
