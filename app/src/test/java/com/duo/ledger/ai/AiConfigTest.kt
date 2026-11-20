package com.duo.ledger.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiConfigTest {
    @Test
    fun blankFallsBackToDefault() {
        assertEquals("https://api.deepseek.com/chat/completions", buildEndpoint(""))
        assertEquals("https://api.deepseek.com/chat/completions", buildEndpoint("   "))
    }

    @Test
    fun defaultBaseUrl() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            buildEndpoint(DEFAULT_AI_BASE_URL)
        )
    }

    @Test
    fun trailingSlashIsRemoved() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            buildEndpoint("https://api.deepseek.com/")
        )
    }

    @Test
    fun v1CompatibleBase() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            buildEndpoint("https://api.deepseek.com/v1")
        )
    }

    @Test
    fun proxyBase() {
        assertEquals(
            "https://proxy.example.com/chat/completions",
            buildEndpoint("  https://proxy.example.com/  ")
        )
    }

    @Test
    fun fullEndpointIsNotDuplicated() {
        val full = "https://proxy.example.com/v1/chat/completions"
        assertEquals(full, buildEndpoint(full))
    }
}
