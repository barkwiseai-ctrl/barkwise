package com.petsocial.app.ui.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class FleaMedicationPlan(
    val brand: String,
    val intervalDays: Int,
    val lastGivenDate: LocalDate,
    val notes: String = "",
)

fun FleaMedicationPlan.nextDueDate(): LocalDate = lastGivenDate.plusDays(intervalDays.toLong())

fun parseFleaMedicationDate(raw: String): LocalDate? = runCatching {
    LocalDate.parse(raw.trim())
}.getOrNull()

fun isFleaMedicationIntervalValid(days: Int?): Boolean = days != null && days in 7..120

fun fleaMedicationReminderDraft(
    dogName: String,
    plan: FleaMedicationPlan,
): CalendarDraft {
    val nextDueDate = plan.nextDueDate()
    val dogLabel = dogName.trim().ifBlank { "your dog" }
    val startMillis = nextDueDate
        .atTime(LocalTime.of(9, 0))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val description = buildString {
        append("Brand: ${plan.brand}")
        append("\nLast given: ${plan.lastGivenDate}")
        append("\nRepeat every ${plan.intervalDays} days")
        append("\nWhen given, rename this occurrence to \"Gave ${plan.brand} to $dogLabel\".")
        if (plan.notes.isNotBlank()) {
            append("\nNotes: ${plan.notes}")
        }
    }
    return CalendarDraft(
        title = "${plan.brand} due for $dogLabel",
        description = description,
        startMillis = startMillis,
        durationMinutes = 15,
        recurrenceRule = "FREQ=DAILY;INTERVAL=${plan.intervalDays}",
        reminderMinutes = listOf(60 * 24 * 3, 60 * 24, 60),
    )
}

fun fleaMedicationLogDraft(
    dogName: String,
    plan: FleaMedicationPlan,
    givenDate: LocalDate,
): CalendarDraft {
    val dogLabel = dogName.trim().ifBlank { "your dog" }
    val nextDueDate = givenDate.plusDays(plan.intervalDays.toLong())
    val startMillis = givenDate
        .atTime(LocalTime.of(9, 0))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val description = buildString {
        append("Brand: ${plan.brand}")
        append("\nGiven on: $givenDate")
        append("\nNext due: $nextDueDate")
        append("\nRepeat every ${plan.intervalDays} days")
        if (plan.notes.isNotBlank()) {
            append("\nNotes: ${plan.notes}")
        }
    }
    return CalendarDraft(
        title = "Gave ${plan.brand} to $dogLabel",
        description = description,
        startMillis = startMillis,
        durationMinutes = 15,
        reminderMinutes = emptyList(),
    )
}
