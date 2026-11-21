package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins {@link UrlSafety} SSRF filtering: loopback, private, link-local, CGNAT and IPv6 unique-local
 * targets are rejected, while a public address passes.
 */
class UrlSafetyTest {

    private static URL url(String spec) throws MalformedURLException {
        return new URL(spec);
    }

    @Test
    void rejectsLoopbackIpv4() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://127.0.0.1/")),
                "a loopback IPv4 target must be rejected");
    }

    @Test
    void rejectsPrivateTenNetwork() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://10.0.0.1/")),
                "a 10.0.0.0/8 private target must be rejected");
    }

    @Test
    void rejectsPrivate192Network() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://192.168.1.1/")),
                "a 192.168.0.0/16 private target must be rejected");
    }

    @Test
    void rejectsLinkLocalMetadataAddress() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://169.254.169.254/")),
                "the cloud metadata link-local address must be rejected");
    }

    @Test
    void rejectsLocalhostName() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://localhost/")),
                "the localhost host name must be rejected, not only its numeric form");
    }

    @Test
    void rejectsCgnatRange() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://100.64.0.1/")),
                "the 100.64.0.0/10 CGNAT range must be rejected");
    }

    @Test
    void rejectsUniqueLocalIpv6() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://[fd00::1]/")),
                "an IPv6 unique-local (fd00::/8) target must be rejected");
    }

    @Test
    void rejectsLoopbackIpv6() throws MalformedURLException {
        assertThrows(IllegalArgumentException.class,
                () -> UrlSafety.validatePublicHost(url("http://[::1]/")),
                "the IPv6 loopback address must be rejected");
    }

    @Test
    void allowsPublicAddress() throws MalformedURLException {
        assertDoesNotThrow(() -> UrlSafety.validatePublicHost(url("http://93.184.216.34/")),
                "a public IPv4 address must pass the SSRF filter");
    }
}
