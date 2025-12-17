package com.airgradient.android.data.provisioning

import com.airgradient.android.domain.models.provisioning.ProvisioningStatusCode
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProvisioningPayloadParserTest {

    private val gson = Gson()

    @Test
    fun `parses json object status`() {
        val payload = """{"status":3}""".toByteArray()
        val status = parseProvisioningStatus(payload, gson)
        assertNotNull(status)
        assertEquals(ProvisioningStatusCode.CONFIGURED, status?.code)
    }

    @Test
    fun `parses json string status`() {
        val payload = "\"12\"".toByteArray()
        val status = parseProvisioningStatus(payload, gson)
        assertNotNull(status)
        assertEquals(ProvisioningStatusCode.CONFIG_PENDING, status?.code)
    }

    @Test
    fun `parses numeric string`() {
        val payload = "10".toByteArray()
        val status = parseProvisioningStatus(payload, gson)
        assertEquals(ProvisioningStatusCode.WIFI_FAILED, status?.code)
    }

    @Test
    fun `parses raw byte`() {
        val payload = byteArrayOf(0x02)
        val status = parseProvisioningStatus(payload, gson)
        assertEquals(ProvisioningStatusCode.SERVER_REACHABLE, status?.code)
    }

    @Test
    fun `unknown code maps to unknown`() {
        val payload = "99".toByteArray()
        val status = parseProvisioningStatus(payload, gson)
        assertEquals(ProvisioningStatusCode.UNKNOWN, status?.code)
    }
}
