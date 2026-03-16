package com.petsocial.app.ui.calendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.petsocial.app.data.CalendarEvent
import com.petsocial.app.data.CommunityEvent
import com.petsocial.app.ui.OwnerBooking
import com.petsocial.app.ui.ProviderBooking
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class CalendarDraft(
    val title: String,
    val description: String = "",
    val location: String = "",
    val startMillis: Long,
    val durationMinutes: Int = 60,
    val recurrenceRule: String? = null,
    val reminderMinutes: List<Int> = listOf(60 * 24, 60),
)

fun Context.openCalendarDraft(draft: CalendarDraft): Boolean {
    val endMillis = draft.startMillis + (draft.durationMinutes.coerceAtLeast(15) * 60_000L)
    val reminderSummary = draft.reminderMinutes
        .asSequence()
        .filter { minutes -> minutes > 0 }
        .distinct()
        .sortedDescending()
        .joinToString(", ") { minutes -> formatReminder(minutes) }
    val descriptionWithReminder = buildString {
        append(draft.description.trim())
        if (isNotBlank()) append("\n\n")
        append("Created in BarkWise")
        if (reminderSummary.isNotBlank()) {
            append("\nDefault reminders: ")
            append(reminderSummary)
        }
    }
    val insertIntent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, draft.title.trim())
        putExtra(CalendarContract.Events.DESCRIPTION, descriptionWithReminder)
        putExtra(CalendarContract.Events.EVENT_LOCATION, draft.location.trim())
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.Events.HAS_ALARM, 1)
        draft.recurrenceRule
            ?.takeIf { rule -> rule.isNotBlank() }
            ?.let { rule -> putExtra(CalendarContract.Events.RRULE, rule) }
        draft.reminderMinutes.firstOrNull()
            ?.takeIf { minutes -> minutes > 0 }
            ?.let { minutes -> putExtra(CalendarContract.Reminders.MINUTES, minutes) }
    }
    val chooser = Intent.createChooser(insertIntent, "Add to calendar")
    if (this !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        startActivity(chooser)
        true
    }.getOrElse { false }
}

fun communityEventToCalendarDraft(
    event: CommunityEvent,
    recurrenceRule: String? = null,
): CalendarDraft? {
    val start = parseIsoInstant(event.date) ?: return null
    val eventRecurrenceRule = recurrenceRule ?: eventRecurrenceRule(event, start)
    return CalendarDraft(
        title = event.title,
        description = event.description,
        location = event.locationName?.ifBlank { null } ?: event.suburb,
        startMillis = start.toEpochMilli(),
        durationMinutes = 75,
        recurrenceRule = eventRecurrenceRule,
    )
}

private fun eventRecurrenceRule(event: CommunityEvent, start: Instant): String? {
    val recurrence = event.recurrence.lowercase()
    val interval = event.recurrenceInterval.coerceAtLeast(1)
    if (recurrence == "none") return null
    return when (recurrence) {
        "daily" -> {
            if (interval == 1) "FREQ=DAILY" else "FREQ=DAILY;INTERVAL=$interval"
        }
        "weekly" -> {
            val dayCode = when (start.atZone(ZoneId.systemDefault()).dayOfWeek) {
                DayOfWeek.MONDAY -> "MO"
                DayOfWeek.TUESDAY -> "TU"
                DayOfWeek.WEDNESDAY -> "WE"
                DayOfWeek.THURSDAY -> "TH"
                DayOfWeek.FRIDAY -> "FR"
                DayOfWeek.SATURDAY -> "SA"
                DayOfWeek.SUNDAY -> "SU"
            }
            if (interval == 1) "FREQ=WEEKLY;BYDAY=$dayCode" else "FREQ=WEEKLY;INTERVAL=$interval;BYDAY=$dayCode"
        }
        "monthly" -> {
            if (interval == 1) "FREQ=MONTHLY" else "FREQ=MONTHLY;INTERVAL=$interval"
        }
        else -> null
    }
}

fun ownerBookingToCalendarDraft(booking: OwnerBooking): CalendarDraft? {
    val start = parseDateAndTimeSlotInstant(booking.date, booking.timeSlot) ?: return null
    val description = buildString {
        if (booking.providerAccountLabel.isNotBlank()) {
            append("Provider: ${booking.providerAccountLabel}\n")
        }
        if (booking.note.isNotBlank()) {
            append("Note: ${booking.note}\n")
        }
        append("Status: ${booking.status.replace("_", " ")}")
    }
    return CalendarDraft(
        title = booking.serviceName,
        description = description,
        location = booking.providerAccountLabel,
        startMillis = start.toEpochMilli(),
        durationMinutes = 60,
    )
}

fun providerBookingToCalendarDraft(booking: ProviderBooking): CalendarDraft? {
    val start = parseDateAndTimeSlotInstant(booking.date, booking.timeSlot) ?: return null
    val description = buildString {
        append("Pet: ${booking.petName}\n")
        if (booking.ownerUserId.isNotBlank()) {
            append("Customer: ${booking.ownerUserId}\n")
        }
        append("Status: ${booking.status.replace("_", " ")}")
    }
    return CalendarDraft(
        title = booking.serviceName,
        description = description,
        startMillis = start.toEpochMilli(),
        durationMinutes = 60,
    )
}

fun calendarEventToCalendarDraft(event: CalendarEvent): CalendarDraft? {
    val start = parseDateAndTimeSlotInstant(event.date, event.timeSlot) ?: return null
    val description = buildString {
        if (event.subtitle.isNotBlank()) {
            append(event.subtitle)
            append('\n')
        }
        append("Type: ${event.type}")
        append('\n')
        append("Status: ${event.status.replace("_", " ")}")
    }
    return CalendarDraft(
        title = event.title,
        description = description,
        startMillis = start.toEpochMilli(),
        durationMinutes = 60,
    )
}

private fun parseDateAndTimeSlotInstant(
    dateRaw: String,
    timeSlotRaw: String?,
): Instant? {
    if (dateRaw.isBlank()) return null
    val zone = ZoneId.systemDefault()
    val parsedIso = parseIsoInstant(dateRaw)
    val baseDate = parseLocalDate(dateRaw)
        ?: parsedIso?.atZone(zone)?.toLocalDate()
        ?: return null
    val parsedTime = parseTimeSlot(timeSlotRaw)
        ?: parsedIso?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)
        ?: LocalTime.of(9, 0)
    return ZonedDateTime.of(baseDate, parsedTime, zone).toInstant()
}

private fun parseLocalDate(raw: String): LocalDate? {
    if (raw.isBlank()) return null
    return runCatching { LocalDate.parse(raw.take(10)) }
        .recoverCatching { LocalDateTime.parse(raw).toLocalDate() }
        .getOrNull()
}

private fun parseTimeSlot(raw: String?): LocalTime? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""(\d{1,2}):(\d{2})""").find(raw) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return LocalTime.of(hours, minutes)
}

private fun parseIsoInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { Instant.parse(raw) }
        .getOrNull()
}

private fun formatReminder(minutes: Int): String {
    return when {
        minutes % (60 * 24) == 0 -> "${minutes / (60 * 24)} day"
        minutes % 60 == 0 -> "${minutes / 60} hour"
        else -> "$minutes min"
    } + if (minutes == 1 || minutes == 60 || minutes == 60 * 24) "" else "s"
}
