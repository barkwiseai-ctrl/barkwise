package com.petsocial.app.ui.qr

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface QrPayloadAction {
    data class InviteToken(val token: String) : QrPayloadAction
    data class FriendToken(val token: String) : QrPayloadAction
    data class FriendConnection(
        val userId: String,
        val humanName: String,
        val dogName: String,
    ) : QrPayloadAction
    data class OpenUrl(val url: String) : QrPayloadAction
    data object Invalid : QrPayloadAction
}

private val inviteTokenRegex = Regex("^inv_[A-Za-z0-9]{6,}$")
private val fallbackTokenRegex = Regex("^[A-Za-z0-9_-]{8,128}$")
private val friendUserIdRegex = Regex("^[A-Za-z0-9_-]{3,128}$")

private fun decodeQueryValue(raw: String): String {
    return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
}

private fun extractInviteToken(rawQuery: String?): String? {
    if (rawQuery.isNullOrBlank()) return null
    for (part in rawQuery.split("&")) {
        if (part.isBlank()) continue
        val pieces = part.split("=", limit = 2)
        val key = decodeQueryValue(pieces[0]).trim().lowercase()
        if (key != "invite_token") continue
        val value = decodeQueryValue(pieces.getOrElse(1) { "" }).trim()
        if (value.isNotBlank()) return value
    }
    return null
}

private fun extractQueryValue(rawQuery: String?, targetKey: String): String? {
    if (rawQuery.isNullOrBlank()) return null
    for (part in rawQuery.split("&")) {
        if (part.isBlank()) continue
        val pieces = part.split("=", limit = 2)
        val key = decodeQueryValue(pieces[0]).trim().lowercase()
        if (key != targetKey) continue
        val value = decodeQueryValue(pieces.getOrElse(1) { "" }).trim()
        if (value.isNotBlank()) return value
    }
    return null
}

private fun extractFriendConnection(parsed: URI): QrPayloadAction.FriendConnection? {
    val scheme = parsed.scheme?.trim()?.lowercase().orEmpty()
    val host = parsed.host?.trim()?.lowercase().orEmpty()
    val path = parsed.path?.trim()?.lowercase().orEmpty()
    val isFriendPayload = (scheme == "barkwise" && (host == "friend" || path == "/friend")) ||
        (scheme in setOf("http", "https") && (path.endsWith("/friend") || path.endsWith("/add-friend")))
    if (!isFriendPayload) return null
    val userId = extractQueryValue(parsed.rawQuery, "user_id")
        ?: extractQueryValue(parsed.rawQuery, "uid")
        ?: return null
    val normalizedUserId = userId.trim()
    if (!friendUserIdRegex.matches(normalizedUserId)) return null
    val humanName = extractQueryValue(parsed.rawQuery, "human_name")
        ?.trim()
        ?.take(48)
        .orEmpty()
        .ifBlank { "BarkWise member" }
    val dogName = extractQueryValue(parsed.rawQuery, "dog_name")
        ?.trim()
        ?.take(48)
        .orEmpty()
        .ifBlank { "Dog" }
    return QrPayloadAction.FriendConnection(
        userId = normalizedUserId,
        humanName = humanName,
        dogName = dogName,
    )
}

fun parseQrPayload(rawValue: String): QrPayloadAction {
    val raw = rawValue.trim()
    if (raw.isBlank()) return QrPayloadAction.Invalid
    if (inviteTokenRegex.matches(raw) || fallbackTokenRegex.matches(raw)) {
        return QrPayloadAction.InviteToken(raw)
    }
    val parsed = runCatching { URI(raw) }.getOrNull()
    if (parsed != null) {
        val inviteToken = extractInviteToken(parsed.rawQuery)?.trim()
        if (!inviteToken.isNullOrBlank()) {
            return QrPayloadAction.InviteToken(inviteToken)
        }
        val friendToken = extractQueryValue(parsed.rawQuery, "friend_token")
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }
        if (friendToken != null) {
            return QrPayloadAction.FriendToken(friendToken)
        }
        extractFriendConnection(parsed)?.let { connection ->
            return connection
        }
        val scheme = parsed.scheme?.trim()?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return QrPayloadAction.OpenUrl(raw)
        }
    }
    return QrPayloadAction.Invalid
}

fun generateQrImageBitmap(
    content: String,
    sizePx: Int = 512,
): ImageBitmap? {
    val trimmed = content.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        val matrix = QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) black else white)
            }
        }
        bitmap.asImageBitmap()
    }.getOrNull()
}
