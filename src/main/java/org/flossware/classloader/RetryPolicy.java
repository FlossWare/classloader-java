package org.flossware.classloader;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines retry behavior for transient failures when loading classes.
 * Supports exponential backoff with jitter to handle network failures gracefully.
 */
public final class RetryPolicy {
    /**
     * Functional interface for operations that may throw IOException.
     *
     * @param <T> The return type
     */
    @FunctionalInterface
    public interface IOSupplier<T> {
        T get() throws IOException;
    }

    /**
     * Jitter percentage divisor: represents 25% jitter (1/4 of the base delay).
     * Used to calculate maximum jitter amount in milliseconds.
     */
    private static final int JITTER_PERCENTAGE_DIVISOR = 4;

    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double backoffMultiplier;
    private final boolean jitter;

    /**
     * Creates a retry policy with specified parameters.
     *
     * @param maxRetries Maximum number of retry attempts (0 = no retries)
     * @param initialDelayMs Initial delay in milliseconds before first retry
     * @param maxDelayMs Maximum delay in milliseconds between retries
     * @param backoffMultiplier Multiplier for exponential backoff (typically 2.0)
     * @param jitter Whether to add random jitter to delays (prevents thundering herd)
     */
    public RetryPolicy(int maxRetries, long initialDelayMs, long maxDelayMs,
                       double backoffMultiplier, boolean jitter) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException("initialDelayMs must be >= 0");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= initialDelayMs");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }

        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.jitter = jitter;
    }

    /**
     * Creates a default retry policy (3 retries, 100ms-10s, exponential backoff, jitter).
     *
     * @return Default retry policy
     */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 100, 10000, 2.0, true);
    }

    /**
     * Creates a no-retry policy (fails immediately).
     *
     * @return No-retry policy
     */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(0, 0, 0, 1.0, false);
    }

    /**
     * Creates an aggressive retry policy (5 retries, longer delays).
     *
     * @return Aggressive retry policy
     */
    public static RetryPolicy aggressive() {
        return new RetryPolicy(5, 200, 30000, 2.0, true);
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param operation The operation to execute
     * @param <T> The return type
     * @return The result of the operation
     * @throws IOException if all retry attempts fail
     */
    public <T> T execute(IOSupplier<T> operation) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (IOException e) {
                lastException = e;

                if (attempt < maxRetries) {
                    long delay = calculateDelay(attempt);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
            }
        }

        throw new IOException("Failed after " + (maxRetries + 1) + " attempts", lastException);
    }

    /**
     * Calculates the delay before the next retry attempt.
     * Uses iterative multiplication to prevent overflow and ThreadLocalRandom for thread-safe jitter.
     *
     * @param attemptNumber The attempt number (0-based)
     * @return Delay in milliseconds
     */
    private long calculateDelay(int attemptNumber) {
        // Exponential backoff with overflow protection
        long delay = initialDelayMs;

        // Multiply iteratively to detect overflow early
        for (int i = 0; i < attemptNumber; i++) {
            // Check if next multiplication would overflow or exceed max
            if (delay > maxDelayMs / backoffMultiplier) {
                delay = maxDelayMs;
                break;
            }
            delay = (long) (delay * backoffMultiplier);
        }

        // Cap at max delay
        delay = Math.min(delay, maxDelayMs);

        // Add proper jitter (±25% random variation) to prevent thundering herd
        if (jitter && delay > 0) {
            long maxJitter = delay / JITTER_PERCENTAGE_DIVISOR;  // 25% of delay
            // ThreadLocalRandom is thread-safe with zero contention (vs synchronized Math.random())
            // Range: [-maxJitter, +maxJitter] for proper jitter distribution
            long jitterAmount = ThreadLocalRandom.current().nextLong(-maxJitter, maxJitter + 1);
            delay = Math.max(0, delay + jitterAmount);  // Ensure non-negative
        }

        return delay;
    }

    /**
     * Gets the maximum number of retry attempts.
     *
     * @return the maximum number of retries (0 means no retries)
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Gets the initial delay before the first retry in milliseconds.
     *
     * @return the initial delay in milliseconds
     */
    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    /**
     * Gets the maximum delay between retries in milliseconds.
     *
     * @return the maximum delay in milliseconds
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    /**
     * Gets the exponential backoff multiplier.
     *
     * @return the backoff multiplier (1.0 or greater)
     */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * Checks whether jitter is enabled for retry delays.
     *
     * @return true if jitter is enabled, false for fixed delays
     */
    public boolean hasJitter() {
        return jitter;
    }

    /**
     * Returns a human-readable string representation of this retry policy
     * including all configuration parameters.
     *
     * @return a formatted string describing this policy
     */
    @Override
    public String toString() {
        return "RetryPolicy[maxRetries=" + maxRetries +
               ", initialDelay=" + initialDelayMs + "ms" +
               ", maxDelay=" + maxDelayMs + "ms" +
               ", backoff=" + backoffMultiplier +
               ", jitter=" + jitter + "]";
    }

    /**
     * Builder for creating RetryPolicy instances with fluent API.
     *
     * <p>Default values:</p>
     * <ul>
     *   <li>maxRetries: 3</li>
     *   <li>initialDelay: 100ms</li>
     *   <li>maxDelay: 10000ms (10 seconds)</li>
     *   <li>backoffMultiplier: 2.0 (exponential)</li>
     *   <li>jitter: true (±25% random variation)</li>
     * </ul>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * RetryPolicy policy = RetryPolicy.builder()
     *     .maxRetries(5)
     *     .initialDelay(Duration.ofMillis(200))
     *     .maxDelay(Duration.ofSeconds(30))
     *     .backoffMultiplier(1.5)
     *     .jitter(true)
     *     .build();
     * }</pre>
     */
    public static class Builder {
        private int maxRetries = 3;
        private long initialDelayMs = 100;
        private long maxDelayMs = 10000;
        private double backoffMultiplier = 2.0;
        private boolean jitter = true;

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxRetries Maximum retry attempts (0 = fail immediately, no retries)
         * @return this builder
         * @throws IllegalArgumentException if maxRetries < 0
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0, got: " + maxRetries);
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the initial delay before the first retry.
         *
         * @param initialDelay Initial delay (must be >= 0 and <= maxDelay)
         * @return this builder
         * @throws IllegalArgumentException if initialDelay < 0 or > maxDelay
         */
        public Builder initialDelay(Duration initialDelay) {
            long ms = initialDelay.toMillis();
            if (ms < 0) {
                throw new IllegalArgumentException("initialDelay must be >= 0, got: " + ms + "ms");
            }
            if (ms > maxDelayMs) {
                throw new IllegalArgumentException(
                    "initialDelay (" + ms + "ms) must be <= maxDelay (currently " + maxDelayMs + "ms)");
            }
            this.initialDelayMs = ms;
            return this;
        }

        /**
         * Sets the maximum delay between retries.
         *
         * @param maxDelay Maximum delay (must be >= initialDelay)
         * @return this builder
         * @throws IllegalArgumentException if maxDelay < 0 or < initialDelay
         */
        public Builder maxDelay(Duration maxDelay) {
            long ms = maxDelay.toMillis();
            if (ms < 0) {
                throw new IllegalArgumentException("maxDelay must be >= 0, got: " + ms + "ms");
            }
            if (ms < initialDelayMs) {
                throw new IllegalArgumentException(
                    "maxDelay (" + ms + "ms) must be >= initialDelay (currently " + initialDelayMs + "ms)");
            }
            this.maxDelayMs = ms;
            return this;
        }

        /**
         * Sets the exponential backoff multiplier.
         *
         * <p>Delay grows as: initialDelay × multiplier^attemptNumber</p>
         * <p>Common values:</p>
         * <ul>
         *   <li>2.0 = exponential (default): 100ms, 200ms, 400ms, 800ms...</li>
         *   <li>1.5 = slower growth: 100ms, 150ms, 225ms, 337ms...</li>
         *   <li>1.0 = linear (no backoff): 100ms, 100ms, 100ms...</li>
         * </ul>
         *
         * @param backoffMultiplier Multiplier for exponential backoff (must be >= 1.0)
         * @return this builder
         * @throws IllegalArgumentException if backoffMultiplier < 1.0
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1.0) {
                throw new IllegalArgumentException(
                    "backoffMultiplier must be >= 1.0, got: " + backoffMultiplier);
            }
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * Enables or disables jitter (±25% random variation in delays).
         *
         * <p>Jitter prevents the "thundering herd" problem where many clients
         * retry simultaneously. When enabled, delays vary by ±25% randomly.</p>
         *
         * <p>Example with 100ms base delay and jitter enabled:</p>
         * <ul>
         *   <li>Possible delays: 75ms, 80ms, 90ms, 100ms, 110ms, 120ms, 125ms</li>
         *   <li>Without jitter: always exactly 100ms</li>
         * </ul>
         *
         * @param jitter true to enable jitter (recommended), false for fixed delays
         * @return this builder
         */
        public Builder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        /**
         * Builds the RetryPolicy with configured parameters.
         *
         * @return A new RetryPolicy instance
         */
        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialDelayMs, maxDelayMs,
                                  backoffMultiplier, jitter);
        }
    }

    /**
     * Creates a new Builder instance for constructing RetryPolicy.
     *
     * @return A new Builder with default values
     */
    public static Builder builder() {
        return new Builder();
    }
}
