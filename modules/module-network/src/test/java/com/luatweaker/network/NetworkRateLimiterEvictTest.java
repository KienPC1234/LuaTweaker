package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logout must release a player's buckets so offline UUIDs pin nothing. */
class NetworkRateLimiterEvictTest {

    private static final String PLAYER_A = "player-a";
    private static final String PLAYER_B = "player-b";
    private static final String CHANNEL = "Trade";

    private static boolean consumeOnce(RateLimiter limiter, String key) {
        return limiter.tryConsumeEvent(key, 1.0, 0.0);
    }

    private static String channelKey(String playerUuid) {
        return playerUuid + '\0' + CHANNEL;
    }

    @Test
    void evictRestoresPlayerBucket() {
        RateLimiter limiter = new RateLimiter();
        consumeOnce(limiter, PLAYER_A);

        assertFalse(consumeOnce(limiter, PLAYER_A),
            "a burst of one must be spent after the first packet");
    }

    @Test
    void evictClearsPlayerAndChannelBuckets() {
        RateLimiter limiter = new RateLimiter();
        consumeOnce(limiter, PLAYER_A);
        consumeOnce(limiter, channelKey(PLAYER_A));
        limiter.evictPlayer(PLAYER_A);

        assertTrue(consumeOnce(limiter, channelKey(PLAYER_A)),
            "eviction must release the per-channel bucket too");
    }

    @Test
    void evictLeavesOtherPlayersAlone() {
        RateLimiter limiter = new RateLimiter();
        consumeOnce(limiter, PLAYER_B);
        limiter.evictPlayer(PLAYER_A);

        assertFalse(consumeOnce(limiter, PLAYER_B),
            "evicting one player must not refill anyone else");
    }
}
