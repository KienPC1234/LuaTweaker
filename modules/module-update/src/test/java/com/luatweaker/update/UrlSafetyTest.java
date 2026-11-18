package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlSafetyTest {

    private static void assertRejected(String url) throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(new URL(url)),
                "must reject internal target: " + url);
    }

    @Test
    void rejectsLoopbackAndPrivateHosts() throws Exception {
        assertRejected("http://127.0.0.1/");
        assertRejected("http://10.0.0.1/");
        assertRejected("http://192.168.1.1/");
        assertRejected("http://169.254.169.254/");
        assertRejected("http://localhost/");
    }

    @Test
    void rejectsCgnatAndUniqueLocalIpv6() throws Exception {
        assertRejected("http://100.64.0.1/");
        assertRejected("http://[fd00::1]/");
        assertRejected("http://[::1]/");
    }

    @Test
    void allowsAPublicAddress() throws Exception {
        assertDoesNotThrow(() -> UrlSafety.validatePublicHost(new URL("http://93.184.216.34/")));
    }
}
