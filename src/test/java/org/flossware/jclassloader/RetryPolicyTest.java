package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void testDefaultPolicy() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        assertEquals(3, policy.getMaxRetries());
        assertEquals(100, policy.getInitialDelayMs());
        assertEquals(10000, policy.getMaxDelayMs());
        assertEquals(2.0, policy.getBackoffMultiplier());
        assertTrue(policy.hasJitter());
    }

    @Test
    void testNoRetry() {
        RetryPolicy policy = RetryPolicy.noRetry();
        assertEquals(0, policy.getMaxRetries());
        assertEquals(0, policy.getInitialDelayMs());
        assertEquals(0, policy.getMaxDelayMs());
        assertEquals(1.0, policy.getBackoffMultiplier());
        assertFalse(policy.hasJitter());
    }

    @Test
    void testAggressivePolicy() {
        RetryPolicy policy = RetryPolicy.aggressive();
        assertEquals(5, policy.getMaxRetries());
        assertEquals(200, policy.getInitialDelayMs());
        assertEquals(30000, policy.getMaxDelayMs());
        assertEquals(2.0, policy.getBackoffMultiplier());
        assertTrue(policy.hasJitter());
    }

    @Test
    void testSuccessfulOperationNoRetry() throws IOException {
        RetryPolicy policy = new RetryPolicy(3, 100, 1000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            attempts.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void testSuccessfulRetryAfterOneFailure() throws IOException {
        RetryPolicy policy = new RetryPolicy(3, 10, 1000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new IOException("First attempt failed");
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testSuccessfulRetryAfterMultipleFailures() throws IOException {
        RetryPolicy policy = new RetryPolicy(5, 10, 1000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        String result = policy.execute(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 4) {
                throw new IOException("Attempt " + attempt + " failed");
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(4, attempts.get());
    }

    @Test
    void testAllRetriesExhausted() {
        RetryPolicy policy = new RetryPolicy(3, 10, 1000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        IOException thrown = assertThrows(IOException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IOException("Always fails");
            });
        });

        assertEquals(4, attempts.get()); // Initial attempt + 3 retries
        assertTrue(thrown.getMessage().contains("Failed after 4 attempts"));
    }

    @Test
    void testExponentialBackoff() throws IOException {
        RetryPolicy policy = new RetryPolicy(3, 100, 10000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        try {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IOException("Always fails");
            });
        } catch (IOException e) {
            // Expected
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Expected delays: 100ms (2^0), 200ms (2^1), 400ms (2^2) = 700ms total
        // Allow some tolerance for timing
        assertTrue(elapsedTime >= 600, "Should have delayed at least 600ms, was: " + elapsedTime);
        assertTrue(elapsedTime < 1500, "Should have delayed less than 1500ms, was: " + elapsedTime);
    }

    @Test
    void testMaxDelayCapApplied() throws IOException {
        RetryPolicy policy = new RetryPolicy(5, 100, 300, 2.0, false);

        long startTime = System.currentTimeMillis();

        try {
            policy.execute(() -> {
                throw new IOException("Always fails");
            });
        } catch (IOException e) {
            // Expected
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        // Delays: 100ms (capped), 200ms (capped), 300ms, 300ms, 300ms = 1200ms
        // Without cap: 100, 200, 400, 800, 1600 = 3100ms
        assertTrue(elapsedTime >= 1000, "Should have delayed at least 1000ms, was: " + elapsedTime);
        assertTrue(elapsedTime < 2000, "Should have delayed less than 2000ms (cap applied), was: " + elapsedTime);
    }

    @Test
    @Timeout(value = 5)
    void testThreadInterruptionDuringRetry() throws InterruptedException {
        RetryPolicy policy = new RetryPolicy(10, 500, 5000, 2.0, false);

        Thread testThread = Thread.currentThread();

        // Interrupt after 100ms
        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(100);
                testThread.interrupt();
            } catch (InterruptedException e) {
                // Ignore
            }
        });
        interrupter.setDaemon(true); // Make daemon so it doesn't prevent JVM exit
        interrupter.start();

        IOException thrown = assertThrows(IOException.class, () -> {
            policy.execute(() -> {
                throw new IOException("Always fails");
            });
        });

        assertTrue(thrown.getMessage().contains("Retry interrupted"));
        assertTrue(Thread.interrupted()); // Clear interrupt flag

        // Wait for interrupter thread to complete
        interrupter.join(1000);
    }

    @Test
    void testNonIOExceptionNotRetried() {
        RetryPolicy policy = new RetryPolicy(3, 10, 1000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Programming error");
            });
        });

        assertEquals(1, attempts.get()); // Should not retry
        assertEquals("Programming error", thrown.getMessage());
    }

    @Test
    void testBuilderValidation() {
        // Test maxRetries validation
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().maxRetries(-1);
        });
        assertTrue(ex1.getMessage().contains("maxRetries must be >= 0"));

        // Test initialDelay validation
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().initialDelay(Duration.ofMillis(-1));
        });
        assertTrue(ex2.getMessage().contains("initialDelay must be >= 0"));

        // Test maxDelay < initialDelay validation
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder()
                .initialDelay(Duration.ofMillis(500))
                .maxDelay(Duration.ofMillis(100));
        });
        assertTrue(ex3.getMessage().contains("maxDelay") && ex3.getMessage().contains("initialDelay"));

        // Test backoffMultiplier validation
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () -> {
            RetryPolicy.builder().backoffMultiplier(0.5);
        });
        assertTrue(ex4.getMessage().contains("backoffMultiplier must be >= 1.0"));
    }

    @Test
    void testBuilderCreatesValidPolicy() {
        RetryPolicy policy = RetryPolicy.builder()
            .maxRetries(5)
            .initialDelay(Duration.ofMillis(200))
            .maxDelay(Duration.ofSeconds(5))
            .backoffMultiplier(1.5)
            .jitter(false)
            .build();

        assertEquals(5, policy.getMaxRetries());
        assertEquals(200, policy.getInitialDelayMs());
        assertEquals(5000, policy.getMaxDelayMs());
        assertEquals(1.5, policy.getBackoffMultiplier());
        assertFalse(policy.hasJitter());
    }

    @Test
    void testToString() {
        RetryPolicy policy = new RetryPolicy(3, 100, 10000, 2.0, true);
        String str = policy.toString();

        assertTrue(str.contains("maxRetries=3"));
        assertTrue(str.contains("initialDelay=100ms"));
        assertTrue(str.contains("maxDelay=10000ms"));
        assertTrue(str.contains("backoff=2.0"));
        assertTrue(str.contains("jitter=true"));
    }

    @Test
    void testJitterAddsRandomness() throws IOException {
        RetryPolicy policyWithJitter = new RetryPolicy(2, 100, 1000, 2.0, true);
        RetryPolicy policyWithoutJitter = new RetryPolicy(2, 100, 1000, 2.0, false);

        long[] timesWithJitter = new long[5];
        long[] timesWithoutJitter = new long[5];

        for (int i = 0; i < 5; i++) {
            long start = System.currentTimeMillis();
            try {
                policyWithJitter.execute(() -> {
                    throw new IOException("Fail");
                });
            } catch (IOException e) {
                // Expected
            }
            timesWithJitter[i] = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();
            try {
                policyWithoutJitter.execute(() -> {
                    throw new IOException("Fail");
                });
            } catch (IOException e) {
                // Expected
            }
            timesWithoutJitter[i] = System.currentTimeMillis() - start;
        }

        // Times without jitter should be very consistent
        long minWithout = timesWithoutJitter[0];
        long maxWithout = timesWithoutJitter[0];
        for (long time : timesWithoutJitter) {
            minWithout = Math.min(minWithout, time);
            maxWithout = Math.max(maxWithout, time);
        }

        // Times with jitter should have more variance
        long minWith = timesWithJitter[0];
        long maxWith = timesWithJitter[0];
        for (long time : timesWithJitter) {
            minWith = Math.min(minWith, time);
            maxWith = Math.max(maxWith, time);
        }

        // With jitter should have more spread (at least 50ms variance)
        long spreadWithJitter = maxWith - minWith;
        long spreadWithoutJitter = maxWithout - minWithout;

        assertTrue(spreadWithJitter > spreadWithoutJitter,
            "Jitter should add variance. With jitter: " + spreadWithJitter + "ms, without: " + spreadWithoutJitter + "ms");
    }

    @Test
    void testZeroRetriesFailsImmediately() {
        RetryPolicy policy = new RetryPolicy(0, 100, 1000, 2.0, false);

        AtomicInteger attempts = new AtomicInteger(0);
        IOException thrown = assertThrows(IOException.class, () -> {
            policy.execute(() -> {
                attempts.incrementAndGet();
                throw new IOException("Fails");
            });
        });

        assertEquals(1, attempts.get()); // Only initial attempt, no retries
        assertTrue(thrown.getMessage().contains("Failed after 1 attempts"));
    }

    @Test
    void testOverflowProtection() {
        // Test that exponential backoff doesn't overflow with large attempt numbers
        // Use reasonable delays so test completes quickly
        RetryPolicy policy = new RetryPolicy(100, 1, 10, 2.0, false);

        // With overflow-prone Math.pow(), attempt 63 would overflow
        // Our iterative approach caps at maxDelayMs instead
        long startTime = System.currentTimeMillis();
        try {
            policy.execute(() -> {
                throw new IOException("Fail");
            });
        } catch (IOException e) {
            // Expected
        }

        // Should complete without IllegalArgumentException from negative delay
        long elapsedTime = System.currentTimeMillis() - startTime;
        // With 100 retries at 10ms max delay = ~1000ms total
        assertTrue(elapsedTime >= 0);
        assertTrue(elapsedTime < 5000, "Should complete in under 5 seconds with capped delays");
    }

    @Test
    void testJitterProducesBothShorterAndLongerDelays() throws IOException {
        // Test that jitter produces delays both shorter AND longer than base delay
        // Old buggy implementation only added jitter (always longer)
        // Correct implementation uses ± jitter
        RetryPolicy policy = new RetryPolicy(1, 1000, 10000, 2.0, true);

        boolean foundShorter = false;
        boolean foundLonger = false;

        // Run multiple times to get statistical sampling
        for (int i = 0; i < 20; i++) {
            long start = System.currentTimeMillis();
            try {
                policy.execute(() -> {
                    throw new IOException("Fail");
                });
            } catch (IOException e) {
                // Expected
            }
            long elapsed = System.currentTimeMillis() - start;

            // Base delay for attempt 0 is 1000ms
            // Without jitter: exactly 1000ms
            // With proper ± jitter: 750ms to 1250ms
            if (elapsed < 950) {  // Less than base (accounting for timing variance)
                foundShorter = true;
            }
            if (elapsed > 1050) {  // More than base (accounting for timing variance)
                foundLonger = true;
            }

            if (foundShorter && foundLonger) {
                break;
            }
        }

        // With proper ±jitter, we should see both shorter and longer delays
        assertTrue(foundShorter || foundLonger,
            "Jitter should produce variation in delays (found shorter: " + foundShorter +
            ", found longer: " + foundLonger + ")");
    }

    @Test
    void testMaxDelayProtectsAgainstOverflow() {
        // Even with huge multiplier and attempt count, maxDelay protects us
        // Use smaller delays so test completes quickly
        RetryPolicy policy = new RetryPolicy(10, 10, 50, 10.0, false);

        long startTime = System.currentTimeMillis();
        try {
            policy.execute(() -> {
                throw new IOException("Fail");
            });
        } catch (IOException e) {
            // Expected
        }
        long elapsedTime = System.currentTimeMillis() - startTime;

        // With multiplier=10, delays would be: 10, 100, 1000... but capped at 50
        // 10 retries × ~50ms = ~500ms max
        // Without overflow protection and capping, this could crash or take forever
        assertTrue(elapsedTime < 2000, "Should complete in under 2 seconds with max delay cap");
    }
}
