package com.snapstreakrecoverer.ssr.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitiesSyncTest {
    @Test
    fun profile_hasDefaultSyncIdAndTimestamps() {
        val profile = Profile(
            profileName = "Test",
            snapchatUsername = "test_user",
            email = "test@example.com",
            mobileNumber = "123456",
            device = "Pixel 8"
        )
        assertNotNull(profile.syncId)
        assertTrue(profile.syncId.isNotEmpty())
        assertTrue(profile.updatedAt > 0)
        assertFalse(profile.isDeleted)
    }

    @Test
    fun friend_hasDefaultSyncIdAndProfileSyncId() {
        val friend = Friend(
            profileId = 1,
            profileSyncId = "test-profile-sync-id",
            username = "friend_user",
            displayName = "Best Friend"
        )
        assertNotNull(friend.syncId)
        assertTrue(friend.syncId.isNotEmpty())
        assertTrue(friend.updatedAt > 0)
        assertFalse(friend.isDeleted)
    }
}
