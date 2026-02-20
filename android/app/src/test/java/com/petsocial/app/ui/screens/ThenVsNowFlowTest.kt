package com.petsocial.app.ui.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThenVsNowFlowTest {

    @Test
    fun validateInputs_requiresValidBirthdayFirst() {
        val error = validateThenVsNowInputs(
            birthdayInput = "2026-99-01",
            thenPhotoUri = "content://then",
            nowPhotoUri = "content://now",
            petName = "Milo",
        )
        assertEquals("Please add a valid birthday.", error)
    }

    @Test
    fun validateInputs_requiresThenPhoto() {
        val error = validateThenVsNowInputs(
            birthdayInput = "2024-01-01",
            thenPhotoUri = null,
            nowPhotoUri = "content://now",
            petName = "Milo",
        )
        assertEquals("Please add a Then photo.", error)
    }

    @Test
    fun validateInputs_passesWhenAllFieldsPresent() {
        val error = validateThenVsNowInputs(
            birthdayInput = "2024-01-01",
            thenPhotoUri = "content://then",
            nowPhotoUri = "content://now",
            petName = "Milo",
        )
        assertNull(error)
    }

    @Test
    fun toHumanAge_formatsYearsAndMonths() {
        val age = toHumanAge(
            from = LocalDate.of(2024, 1, 1),
            to = LocalDate.of(2026, 3, 1),
        )
        assertEquals("2 years, 2 months", age)
    }

    @Test
    fun buildWhatChangedBullets_usesWeightDeltaWhenPresent() {
        val bullets = buildWhatChangedBullets(
            weightThen = 4.0f,
            weightNow = 5.2f,
            milestone = "potty trained",
        )
        assertEquals("Weight changed up 1.2 kg", bullets[0])
        assertEquals("Milestone: potty trained", bullets[2])
    }

    @Test
    fun buildNextFocusTip_selectsLifeStageBuckets() {
        val puppyTip = buildNextFocusTip(
            birthday = LocalDate.of(2025, 9, 1),
            today = LocalDate.of(2026, 2, 1),
        )
        assertEquals("Keep training sessions short: 5-8 minutes, 4x/week.", puppyTip)

        val adultTip = buildNextFocusTip(
            birthday = LocalDate.of(2022, 1, 1),
            today = LocalDate.of(2026, 2, 1),
        )
        assertEquals("Add 10 minutes of sniff walk 3x/week for enrichment.", adultTip)

        val seniorTip = buildNextFocusTip(
            birthday = LocalDate.of(2018, 1, 1),
            today = LocalDate.of(2026, 2, 1),
        )
        assertEquals("Schedule one low-impact mobility session weekly and monitor recovery time.", seniorTip)
    }
}
