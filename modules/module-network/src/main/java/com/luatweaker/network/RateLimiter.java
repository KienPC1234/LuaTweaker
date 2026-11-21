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

    /** Number of per-player buckets kept before idle ones are evicted. */
    private static final int MAX_BUDGET_ENTRIES = 4_096;
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
