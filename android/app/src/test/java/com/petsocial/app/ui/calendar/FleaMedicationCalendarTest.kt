package com.petsocial.app.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FleaMedicationCalendarTest {

    @Test
    fun nextDueDate_addsIntervalToLastGivenDate() {
        val plan = FleaMedicationPlan(
            brand = "NexGard",
            intervalDays = 30,
            lastGivenDate = LocalDate.parse("2026-04-27"),
            notes = "Large dog chew",
        )

        assertEquals(LocalDate.parse("2026-05-27"), plan.nextDueDate())
    }

    @Test
    fun reminderDraft_usesRecurringRuleAndIncludesNotes() {
        val draft = fleaMedicationReminderDraft(
            dogName = "Milo",
            plan = FleaMedicationPlan(
                brand = "NexGard",
                intervalDays = 30,
                lastGivenDate = LocalDate.parse("2026-04-27"),
                notes = "Large dog chew",
            ),
        )

        assertEquals("NexGard due for Milo", draft.title)
        assertEquals("FREQ=DAILY;INTERVAL=30", draft.recurrenceRule)
        assertEquals(listOf(4320, 1440, 60), draft.reminderMinutes)
        assertTrue(draft.description.contains("Large dog chew"))
    }

    @Test
    fun parseFleaMedicationDate_returnsNullForInvalidDate() {
        assertNull(parseFleaMedicationDate("2026-02-30"))
    }
}
