package br.com.vertice81.nexir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    @Test fun allowsOnlyPrivateIpv4ForManualRoku() {
        listOf("192.168.0.10", "10.10.10.20", "172.16.1.2", "172.31.255.254")
            .forEach { assertTrue(it, RokuControl.isPrivateIpv4(it)) }
        listOf("8.8.8.8", "127.0.0.1", "169.254.1.1", "172.32.0.2",
            "192.168.1.999", "192.168.example.1", "::1", "localhost")
            .forEach { assertFalse(it, RokuControl.isPrivateIpv4(it)) }
    }

    @Test fun onlyDocumentedRemoteKeysAreEnabledForRoku() {
        assertTrue(RokuControl.supports("HOME"))
        assertTrue(RokuControl.supports("OK"))
        assertTrue(RokuControl.supports("HDMI_1"))
        assertFalse(RokuControl.supports("SOURCE"))
        assertFalse(RokuControl.supports("RESTART_DEVICE"))
    }
}
