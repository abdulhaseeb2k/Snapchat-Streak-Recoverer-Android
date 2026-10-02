package com.snapstreakrecoverer.ssr.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDeduplicationTest {

    @Test
    fun matchesByExactSyncId() {
        assertTrue(
            isSameProfile(
                syncId1 = "uuid-1",
                snapchatUsername1 = "userA",
                profileName1 = "LabelA",
                syncId2 = "uuid-1",
                snapchatUsername2 = "userB",
                profileName2 = "LabelB"
            )
        )
    }

    @Test
    fun matchesByNormalizedSnapchatUsername() {
        assertTrue(
            isSameProfile(
                syncId1 = "uuid-1",
                snapchatUsername1 = "@Haseeb_2k ",
                profileName1 = "Phone 1 Label",
                syncId2 = "uuid-2",
                snapchatUsername2 = "haseeb_2k",
                profileName2 = "Phone 2 Label"
            )
        )
    }

    @Test
    fun matchesByNormalizedProfileName() {
        assertTrue(
            isSameProfile(
                syncId1 = "uuid-1",
                snapchatUsername1 = "",
                profileName1 = " Haseeb ",
                syncId2 = "uuid-2",
                snapchatUsername2 = "",
                profileName2 = "haseeb"
            )
        )
    }

    @Test
    fun distinguishesCompletelyDifferentProfiles() {
        assertFalse(
            isSameProfile(
                syncId1 = "uuid-1",
                snapchatUsername1 = "haseeb",
                profileName1 = "Haseeb",
                syncId2 = "uuid-2",
                snapchatUsername2 = "libra",
                profileName2 = "Libra"
            )
        )
    }

    @Test
    fun friendMatchesByNormalizedUsername() {
        assertTrue(
            isSameFriend(
                syncId1 = "f-1",
                username1 = "@JohnDoe",
                syncId2 = "f-2",
                username2 = "johndoe"
            )
        )
    }
}
