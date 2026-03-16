package com.petsocial.app.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeUtilsTest {

    @Test
    fun parseDeepLinkWithInviteToken() {
        val action = parseQrPayload("barkwise://join?invite_token=inv_ABC12345")
        assertTrue(action is QrPayloadAction.InviteToken)
        assertEquals("inv_ABC12345", (action as QrPayloadAction.InviteToken).token)
    }

    @Test
    fun parseHttpsJoinWithInviteToken() {
        val action = parseQrPayload("https://example.com/join?invite_token=inv_999999")
        assertTrue(action is QrPayloadAction.InviteToken)
        assertEquals("inv_999999", (action as QrPayloadAction.InviteToken).token)
    }

    @Test
    fun parseEncodedInviteTokenFromQuery() {
        val action = parseQrPayload("barkwise://join?invite_token=inv_test%2Dtoken_123")
        assertTrue(action is QrPayloadAction.InviteToken)
        assertEquals("inv_test-token_123", (action as QrPayloadAction.InviteToken).token)
    }

    @Test
    fun parseRawInviteTokenFallback() {
        val action = parseQrPayload("token_12345678")
        assertTrue(action is QrPayloadAction.InviteToken)
        assertEquals("token_12345678", (action as QrPayloadAction.InviteToken).token)
    }

    @Test
    fun parseInstallUrlAsOpenUrl() {
        val url = "https://barkwise.app/install"
        val action = parseQrPayload(url)
        assertTrue(action is QrPayloadAction.OpenUrl)
        assertEquals(url, (action as QrPayloadAction.OpenUrl).url)
    }

    @Test
    fun parseInvalidPayload() {
        val action = parseQrPayload("not a qr payload")
        assertTrue(action is QrPayloadAction.Invalid)
    }
}

