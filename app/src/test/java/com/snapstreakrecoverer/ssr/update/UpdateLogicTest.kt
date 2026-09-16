package com.snapstreakrecoverer.ssr.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    @Test
    fun testIsNewerVersion_newerPatch_returnsTrue() {
        assertTrue(isNewerVersion("1.1.1", "1.1.0"))
        assertTrue(isNewerVersion("v1.1.1", "1.1.0"))
    }

    @Test
    fun testIsNewerVersion_newerMinor_returnsTrue() {
        assertTrue(isNewerVersion("1.2.0", "1.1.0"))
        assertTrue(isNewerVersion("v1.2.0", "v1.1.0"))
    }

    @Test
    fun testIsNewerVersion_newerMajor_returnsTrue() {
        assertTrue(isNewerVersion("2.0.0", "1.1.0"))
    }

    @Test
    fun testIsNewerVersion_sameVersion_returnsFalse() {
        assertFalse(isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(isNewerVersion("v1.1.0", "1.1.0"))
    }

    @Test
    fun testIsNewerVersion_olderVersion_returnsFalse() {
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(isNewerVersion("1.0.9", "1.1.0"))
    }
}
