package com.petsocial.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesMapAutofitRegressionTest {

    @Test
    fun userInteracted_sameDataSignature_doesNotAutoFit() {
        val shouldAutoFit = shouldAutoFitServicesMap(
            lastAutoFitDataSignature = "stable-data",
            currentDataSignature = "stable-data",
            userHasInteractedWithMap = true,
        )

        assertFalse(shouldAutoFit)
    }

    @Test
    fun userInteracted_dataSignatureChanged_autoFits() {
        val shouldAutoFit = shouldAutoFitServicesMap(
            lastAutoFitDataSignature = "old-data",
            currentDataSignature = "new-data",
            userHasInteractedWithMap = true,
        )

        assertTrue(shouldAutoFit)
    }

    @Test
    fun noUserInteraction_autoFitsForInitialAndRefreshCameraSetup() {
        val shouldAutoFit = shouldAutoFitServicesMap(
            lastAutoFitDataSignature = "stable-data",
            currentDataSignature = "stable-data",
            userHasInteractedWithMap = false,
        )

        assertTrue(shouldAutoFit)
    }
}
