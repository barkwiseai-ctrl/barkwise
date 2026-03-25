package com.petsocial.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerResolutionTest {

    @Test
    fun resolveBarkAiComposerEnabled_disablesRegularChatWhileLoading() {
        assertFalse(
            resolveBarkAiComposerEnabled(
                loading = true,
                onboardingMode = false,
            ),
        )
    }

    @Test
    fun resolveBarkAiComposerEnabled_keepsOnboardingComposerEnabledWhileLoading() {
        assertTrue(
            resolveBarkAiComposerEnabled(
                loading = true,
                onboardingMode = true,
            ),
        )
    }

    @Test
    fun resolveBarkAiComposerEnabled_enablesComposerWhenIdle() {
        assertTrue(
            resolveBarkAiComposerEnabled(
                loading = false,
                onboardingMode = false,
            ),
        )
    }
}
