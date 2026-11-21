package com.luatweaker.update;

import org.jetbrains.annotations.NotNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Blocks SSRF targets for user/ mod supplied URLs: loopback, link-local, private and unique-local
 * ranges are rejected after DNS resolution, so a hostname that resolves to an internal address is
 * refused too.
 */
public final class UrlSafety {

    /**
     * Test-only escape hatch for local HTTP servers. Never set this in production.
     */
    public static final String ALLOW_INTERNAL_PROPERTY = "luatweaker.net.allowInternalHosts";

    private UrlSafety() {
    }

    /**
     * Rejects a URL whose host is (or resolves to) a loopback/private/link-local address.
     *
     * @param url parsed URL
     * @throws IllegalArgumentException when the target is not a public internet host
     */
    public static void validatePublicHost(@NotNull URL url) {
        if (Boolean.getBoolean(ALLOW_INTERNAL_PROPERTY)) {
            return;
        }
        String host = requireHost(url);
        rejectLocalName(host);
        for (InetAddress address : resolveAll(host)) {
            if (isInternal(address)) {
                throw new IllegalArgumentException(
                        "URL host '" + host + "' resolves to a non-public address (" + address.getHostAddress() + ")");
            }
        }
    }

    @NotNull
    private static String requireHost(@NotNull URL url) {
        String host = url.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        return host;
    }

    private static void rejectLocalName(@NotNull String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
            throw new IllegalArgumentException("URL host is local: " + host);
        }
    }

    @NotNull
    private static InetAddress[] resolveAll(@NotNull String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve URL host '" + host + "': " + e.getMessage(), e);
        }
    }

    private static boolean isInternal(@NotNull InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            // 0.0.0.0/8, 10/8, 100.64/10 (CGNAT), 127/8, 169.254/16, 172.16/12, 192.168/16
            return first == 0 || first == 10 || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            // fc00::/7 unique-local and fe80::/10 link-local.
            return (bytes[0] & 0xfe) == 0xfc || ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xc0) == 0x80);
        }
        return false;
    }
}
