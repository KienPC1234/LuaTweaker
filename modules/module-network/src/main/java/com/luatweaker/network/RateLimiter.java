package com.luatweaker.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-player token buckets that cap RemoteFunction/RemoteEvent throughput.
 * Bursts absorb login spikes; the sustained rate drops floods to an error
 * instead of letting one client amplify work on the other side.
 */
final class RateLimiter {

    /**
     * Number of per-key buckets kept before idle ones are evicted. Keys are
     * per player and per (player, channel), so the cap covers players times
     * channels; overflow clears (a blunt memory backstop, never a signal).
     */
    private static final int MAX_BUDGET_ENTRIES = 16_384;
    private static final double TOKENS_PER_REQUEST = 1.0;

    private final Map<String, TokenBucket> rpcBudgets = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> eventBudgets = new ConcurrentHashMap<>();

    boolean tryConsumeRpc(String key, double burst, double refillPerSecond) {
        return consumeBudget(rpcBudgets, key, burst, refillPerSecond);
    }

    boolean tryConsumeEvent(String key, double burst, double refillPerSecond) {
        return consumeBudget(eventBudgets, key, burst, refillPerSecond);
    }

    private boolean consumeBudget(Map<String, TokenBucket> budgets, String key,
                                  double burst, double refillPerSecond) {
        if (budgets.size() > MAX_BUDGET_ENTRIES) {
            budgets.clear();
        }
        return budgets.computeIfAbsent(key, ignored -> new TokenBucket(burst, refillPerSecond)).tryConsume();
    }

    /**
     * Drops every bucket attributed to a player: their bare UUID keys, their
     * per-channel keys, and their server-to-client keys. Called on logout so
     * offline UUIDs never pin buckets (and a rejoin starts from full budgets
     * instead of inheriting a drained one). Global keys
     * ({@code srv-broadcast}, {@code local-client}) are shared and stay.
     */
    void evictPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }
        evictMatching(rpcBudgets, playerUuid);
        evictMatching(eventBudgets, playerUuid);
    }

    private static void evictMatching(Map<String, TokenBucket> budgets, String playerUuid) {
        String channelPrefix = playerUuid + '\0';
        String outboundPrefix = "srv->" + playerUuid;
        budgets.keySet().removeIf(key -> key.equals(playerUuid)
                || key.startsWith(channelPrefix)
                || key.equals(outboundPrefix)
                || key.startsWith(outboundPrefix + '\0'));
    }

    private static final class TokenBucket {
        private final double burst;
        private final double refillPerSecond;
        private final ReentrantLock lock = new ReentrantLock();
        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        private TokenBucket(double burst, double refillPerSecond) {
            this.burst = burst;
            this.refillPerSecond = refillPerSecond;
            this.tokens = burst;
        }

        private boolean tryConsume() {
            lock.lock();
            try {
                long now = System.nanoTime();
                double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
                lastRefillNanos = now;
                tokens = Math.min(burst, tokens + elapsedSeconds * refillPerSecond);
                if (tokens < TOKENS_PER_REQUEST) {
                    return false;
                }
                tokens -= TOKENS_PER_REQUEST;
                return true;
            } finally {
                lock.unlock();
            }
        }
    }
}
