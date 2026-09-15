package com.snapstreakrecoverer.ssr.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthStateTest {
    @Test
    fun authState_statesBehaveCorrectly() {
        val unauthenticated = AuthState.Unauthenticated
        val loading = AuthState.Loading
        val error = AuthState.Error("Network error")

        assertTrue(unauthenticated is AuthState.Unauthenticated)
        assertTrue(loading is AuthState.Loading)
        assertEquals("Network error", (error as AuthState.Error).message)
    }
}
