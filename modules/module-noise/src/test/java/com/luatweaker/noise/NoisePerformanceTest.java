package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Performance gate: one million simplex evaluations must stay under two seconds.
 */
class NoisePerformanceTest {

    private static final long LIMIT_MS = 2000L;
    private static final int EVALUATIONS = 1_000_000;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void oneMillionEvaluationsStayUnderTwoSeconds() {
        NoiseServiceImpl service = new NoiseServiceImpl(53L);
        long start = System.nanoTime();
        double sum = 0.0;
        for (int i = 0; i < EVALUATIONS; i++) {
            sum += service.simplex(i * 0.001, i * 0.002, 1.0);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        Assertions.assertTrue(elapsedMs < LIMIT_MS && Double.isFinite(sum),
                "1M simplex evals took " + elapsedMs + "ms (limit " + LIMIT_MS + "ms), sum=" + sum);
    }
}
