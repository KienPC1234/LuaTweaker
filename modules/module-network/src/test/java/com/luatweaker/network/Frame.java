package com.luatweaker.network;

/** One message the service handed to the transport, matching the real byte API. */
record Frame(String target, String channel, byte[] data) {
}
