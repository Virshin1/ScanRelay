package com.scanrelay.app.util

import com.scanrelay.app.data.remote.NtfyClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyMessageParsingTest {

    @Test
    fun `parse valid ntfy message event line`() {
        val jsonLine = """{"id":"msg_123","time":1729600000,"event":"message","topic":"scanrelay_test","message":"upi://pay?pa=shop@upi&am=50","title":"UPI Payment","click":"upi://pay?pa=shop@upi&am=50"}"""
        val parsed = NtfyClient.parseNtfyJsonLine(jsonLine)

        assertNotNull(parsed)
        assertEquals("msg_123", parsed?.id)
        assertEquals(1729600000L, parsed?.time)
        assertEquals("message", parsed?.event)
        assertEquals("scanrelay_test", parsed?.topic)
        assertEquals("upi://pay?pa=shop@upi&am=50", parsed?.message)
        assertEquals("UPI Payment", parsed?.title)
        assertEquals("upi://pay?pa=shop@upi&am=50", parsed?.click)
    }

    @Test
    fun `parse ntfy keepalive and open events`() {
        val openLine = """{"id":"init","time":1729600000,"event":"open","topic":"scanrelay_test"}"""
        val openMsg = NtfyClient.parseNtfyJsonLine(openLine)
        assertNotNull(openMsg)
        assertEquals("open", openMsg?.event)
        assertNull(openMsg?.message)

        val keepaliveLine = """{"event":"keepalive","topic":"scanrelay_test"}"""
        val keepaliveMsg = NtfyClient.parseNtfyJsonLine(keepaliveLine)
        assertNotNull(keepaliveMsg)
        assertEquals("keepalive", keepaliveMsg?.event)
        assertNull(keepaliveMsg?.message)
    }

    @Test
    fun `malformed json line returns null cleanly`() {
        val broken = "not a json line"
        val parsed = NtfyClient.parseNtfyJsonLine(broken)
        assertNull(parsed)
    }
}
