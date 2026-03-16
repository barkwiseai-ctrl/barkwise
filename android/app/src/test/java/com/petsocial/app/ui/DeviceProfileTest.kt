package com.petsocial.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceProfileTest {

    @Test
    fun phoneSizeClass_forCompactWidth() {
        val result = phoneSizeClassFor(smallestWidthDp = 320, screenWidthDp = 360)
        assertEquals(PhoneSizeClass.Compact, result)
    }

    @Test
    fun phoneSizeClass_forLargeWidth() {
        val result = phoneSizeClassFor(smallestWidthDp = 411, screenWidthDp = 500)
        assertEquals(PhoneSizeClass.Large, result)
    }

    @Test
    fun scannerPreviewHeight_cappedByScreenHeight() {
        val compact = scannerPreviewHeightDp(
            sizeClass = PhoneSizeClass.Compact,
            screenHeightDp = 360,
        )
        assertEquals(220f, compact.value)

        val large = scannerPreviewHeightDp(
            sizeClass = PhoneSizeClass.Large,
            screenHeightDp = 640,
        )
        assertEquals(384f, large.value)
    }
}
