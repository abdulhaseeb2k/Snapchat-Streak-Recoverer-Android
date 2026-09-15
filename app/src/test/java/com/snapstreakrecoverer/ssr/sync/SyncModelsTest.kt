package com.snapstreakrecoverer.ssr.sync

import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncModelsTest {
    @Test
    fun profile_convertsToAndFromFirestoreMap() {
        val profile = Profile(
            id = 10,
            syncId = "prof-123",
            profileName = "Personal",
            snapchatUsername = "snap_person",
            email = "user@snap.com",
            mobileNumber = "555-0199",
            device = "Samsung S24",
            refreshDelay = 1.5,
            updatedAt = 1700000000L,
            isDeleted = false
        )
        val map = profile.toFirestoreMap()
        val restored = mapToFirestoreProfile(map)

        assertEquals("prof-123", restored.syncId)
        assertEquals("Personal", restored.profileName)
        assertEquals("snap_person", restored.snapchatUsername)
        assertEquals("user@snap.com", restored.email)
        assertEquals("555-0199", restored.mobileNumber)
        assertEquals("Samsung S24", restored.device)
        assertEquals(1.5, restored.refreshDelay, 0.001)
        assertEquals(1700000000L, restored.updatedAt)
        assertEquals(false, restored.isDeleted)
    }

    @Test
    fun friend_convertsToAndFromFirestoreMap() {
        val friend = Friend(
            id = 5,
            syncId = "friend-456",
            profileId = 10,
            profileSyncId = "prof-123",
            username = "alice",
            displayName = "Alice Wonderland",
            isSelected = true,
            updatedAt = 1700000050L,
            isDeleted = false
        )
        val map = friend.toFirestoreMap()
        val restored = mapToFirestoreFriend(map)

        assertEquals("friend-456", restored.syncId)
        assertEquals("prof-123", restored.profileSyncId)
        assertEquals("alice", restored.username)
        assertEquals("Alice Wonderland", restored.displayName)
        assertEquals(true, restored.isSelected)
        assertEquals(1700000050L, restored.updatedAt)
        assertEquals(false, restored.isDeleted)
    }
}
