package com.komugi.attacker.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadsTest {

    @Test
    fun `every built in payload fits the legacy advertisement budget`() {
        Payloads.all.forEach { payload ->
            assertTrue(
                "${payload.id} is ${payload.data.size} bytes",
                payload.data.size <= Payloads.MAX_PAYLOAD_BYTES,
            )
        }
    }

    @Test
    fun `apple proximity payload carries the documented header`() {
        val payload = Payloads.apple.first { it.id == "apple_2" }
        assertEquals(0x004C, payload.companyId)
        assertEquals("AirPods", payload.label)
        assertEquals(0x07, payload.data[0].toInt() and 0xFF)
        assertEquals(0x19, payload.data[1].toInt() and 0xFF)
        assertEquals(0x07, payload.data[2].toInt() and 0xFF)
        assertEquals(0x02, payload.data[3].toInt() and 0xFF)
    }

    @Test
    fun `apple tv payload keeps the variant byte`() {
        val payload = Payloads.appleTv.first { it.label == "AppleTV Pair" }
        assertEquals(0x06, payload.data[9].toInt() and 0xFF)
        assertEquals(0x004C, payload.companyId)
    }

    @Test
    fun `custom hex with company prefix is split at the company id`() {
        val payload = Payloads.parseCustom("4C 00 07 19 07 02 20 75 AA 30", null)
        assertNotNull(payload)
        assertEquals(0x004C, payload!!.companyId)
        assertEquals(8, payload.data.size)
        assertEquals(0x07, payload.data[0].toInt() and 0xFF)
    }

    @Test
    fun `custom hex honours an explicit company override`() {
        val payload = Payloads.parseCustom("030300800000", 0x0006)
        assertNotNull(payload)
        assertEquals(0x0006, payload!!.companyId)
        assertEquals(6, payload.data.size)
    }

    @Test
    fun `malformed custom hex is rejected`() {
        assertNull(Payloads.parseCustom("4C0007190", null))
        assertNull(Payloads.parseCustom("ZZZZZZ", null))
        assertNull(Payloads.parseCustom("", null))
    }

    @Test
    fun `oversized custom hex is rejected`() {
        val long = "4C00" + "11".repeat(Payloads.MAX_PAYLOAD_BYTES + 1)
        assertNull(Payloads.parseCustom(long, null))
    }
}
