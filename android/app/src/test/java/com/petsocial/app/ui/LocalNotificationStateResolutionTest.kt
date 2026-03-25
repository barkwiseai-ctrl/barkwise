package com.petsocial.app.ui

import com.petsocial.app.data.AppNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNotificationStateResolutionTest {

    @Test
    fun resolveLocalNotificationRead_marksSingleLocalNotificationRead() {
        val resolution = resolveLocalNotificationRead(
            notifications = listOf(
                notification(id = "local:one", read = false),
                notification(id = "remote:two", read = false),
            ),
            readLocalNotificationIds = emptySet(),
            notificationId = " local:one ",
        )

        assertEquals(setOf("local:one"), resolution.readLocalNotificationIds)
        assertTrue(resolution.notifications.first { it.id == "local:one" }.read)
        assertEquals(false, resolution.notifications.first { it.id == "remote:two" }.read)
    }

    @Test
    fun resolveLocalNotificationIdsRead_marksOnlyLocalIdsAndKeepsToast() {
        val resolution = resolveLocalNotificationIdsRead(
            notifications = listOf(
                notification(id = "local:one", read = false),
                notification(id = "remote:two", read = false),
            ),
            readLocalNotificationIds = setOf("local:existing"),
            notificationIds = setOf("local:one", "remote:two"),
            toastMessage = "Local notifications marked read",
        )

        assertEquals(setOf("local:existing", "local:one"), resolution.readLocalNotificationIds)
        assertTrue(resolution.notifications.first { it.id == "local:one" }.read)
        assertEquals(false, resolution.notifications.first { it.id == "remote:two" }.read)
        assertEquals("Local notifications marked read", resolution.toastMessage)
    }

    @Test
    fun resolveClearAllLocalNotifications_removesOnlyLocalNotifications() {
        val resolution = resolveClearAllLocalNotifications(
            notifications = listOf(
                notification(id = "local:one", read = true),
                notification(id = "remote:two", read = false),
            ),
            toastMessage = "Local notifications cleared",
        )

        assertEquals(listOf("remote:two"), resolution.notifications.map { it.id })
        assertTrue(resolution.readLocalNotificationIds.isEmpty())
        assertEquals("Local notifications cleared", resolution.toastMessage)
    }

    @Test
    fun resolveClearLocalNotificationIds_removesIdsAndAcknowledgements() {
        val resolution = resolveClearLocalNotificationIds(
            notifications = listOf(
                notification(id = "local:one", read = true),
                notification(id = "local:two", read = true),
                notification(id = "remote:three", read = false),
            ),
            readLocalNotificationIds = setOf("local:one", "local:two"),
            acknowledgedCommunityNotificationIds = setOf("local:one", "community"),
            acknowledgedMessageNotificationIds = setOf("local:two", "message"),
            notificationIds = listOf(" local:one ", "remote:three"),
        )

        assertEquals(listOf("local:two", "remote:three"), resolution.notifications.map { it.id })
        assertEquals(setOf("local:two"), resolution.readLocalNotificationIds)
        assertEquals(setOf("community"), resolution.acknowledgedCommunityNotificationIds)
        assertEquals(setOf("local:two", "message"), resolution.acknowledgedMessageNotificationIds)
        assertEquals("Local notifications cleared", resolution.toastMessage)
    }

    @Test
    fun resolveClearLocalNotificationIds_noLocalIdsIsNoOp() {
        val notifications = listOf(notification(id = "remote:three", read = false))
        val resolution = resolveClearLocalNotificationIds(
            notifications = notifications,
            readLocalNotificationIds = setOf("local:one"),
            acknowledgedCommunityNotificationIds = setOf("community"),
            acknowledgedMessageNotificationIds = setOf("message"),
            notificationIds = listOf("remote:three"),
        )

        assertEquals(notifications, resolution.notifications)
        assertEquals(setOf("local:one"), resolution.readLocalNotificationIds)
        assertEquals(setOf("community"), resolution.acknowledgedCommunityNotificationIds)
        assertEquals(setOf("message"), resolution.acknowledgedMessageNotificationIds)
        assertEquals(null, resolution.toastMessage)
    }

    private fun notification(id: String, read: Boolean): AppNotification {
        return AppNotification(
            id = id,
            userId = "user_2",
            title = "Title",
            body = "Body",
            category = "message",
            read = read,
            createdAt = "2026-03-20T00:00:00Z",
        )
    }
}
