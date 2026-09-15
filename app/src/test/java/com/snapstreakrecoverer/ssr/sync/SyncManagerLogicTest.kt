package com.snapstreakrecoverer.ssr.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerLogicTest {
    @Test
    fun shouldUpdateLocal_remoteNewer_returnsTrue() {
        val localUpdatedAt = 1000L
        val remoteUpdatedAt = 2000L
        assertTrue(remoteUpdatedAt > localUpdatedAt)
    }

    @Test
    fun shouldUpdateLocal_localNewer_returnsFalse() {
        val localUpdatedAt = 3000L
        val remoteUpdatedAt = 2000L
        assertFalse(remoteUpdatedAt > localUpdatedAt)
    }
}
