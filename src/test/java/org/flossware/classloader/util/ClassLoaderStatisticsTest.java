package org.flossware.classloader.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassLoaderStatisticsTest {

    @Test
    void testConstructorAndGetters() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "test-loader",
            100,
            5_000_000L,
            75L
        );

        assertEquals("test-loader", stats.getName());
        assertEquals(100, stats.getClassesLoaded());
        assertEquals(5_000_000L, stats.getTotalBytesLoaded());
        assertEquals(75L, stats.getCacheHits());
    }

    @Test
    void testCacheHitRateCalculation() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            100,
            1_000_000L,
            75L
        );

        assertEquals(0.75, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testCacheHitRateWithNoCacheHits() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            100,
            1_000_000L,
            0L
        );

        assertEquals(0.0, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testCacheHitRateWithAllCacheHits() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            50,
            500_000L,
            50L
        );

        assertEquals(1.0, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testCacheHitRateWithNoClassesLoaded() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            0,
            0L,
            0L
        );

        assertEquals(0.0, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testCacheHitRateWithPartialHits() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            200,
            10_000_000L,
            50L
        );

        assertEquals(0.25, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testToString() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "my-loader",
            100,
            2_000_000L,
            80L
        );

        String str = stats.toString();

        assertTrue(str.contains("ClassLoaderStatistics"));
        assertTrue(str.contains("name=my-loader"));
        assertTrue(str.contains("classes=100"));
        assertTrue(str.contains("bytes=2000000"));
        assertTrue(str.contains("cacheHits=80"));
        assertTrue(str.contains("hitRate=80.00%"));
    }

    @Test
    void testToStringWithZeroHitRate() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            50,
            1_000_000L,
            0L
        );

        String str = stats.toString();
        assertTrue(str.contains("hitRate=0.00%"));
    }

    @Test
    void testToStringWith100PercentHitRate() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            100,
            5_000_000L,
            100L
        );

        String str = stats.toString();
        assertTrue(str.contains("hitRate=100.00%"));
    }

    @Test
    void testWithNullName() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            null,
            10,
            1000L,
            5L
        );

        assertNull(stats.getName());
        assertEquals(10, stats.getClassesLoaded());
    }

    @Test
    void testWithEmptyName() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "",
            10,
            1000L,
            5L
        );

        assertEquals("", stats.getName());
    }

    @Test
    void testWithNegativeValues() {
        // Test that negative values are stored as-is (no validation in constructor)
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            -10,
            -1000L,
            -5L
        );

        assertEquals(-10, stats.getClassesLoaded());
        assertEquals(-1000L, stats.getTotalBytesLoaded());
        assertEquals(-5L, stats.getCacheHits());
    }

    @Test
    void testCacheHitRateWithNegativeClassesLoaded() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            -10,
            1000L,
            5L
        );

        // When classesLoaded <= 0, getCacheHitRate() returns 0.0
        assertEquals(0.0, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testLargeNumbers() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "large-loader",
            1_000_000,
            10_000_000_000L,
            750_000L
        );

        assertEquals(1_000_000, stats.getClassesLoaded());
        assertEquals(10_000_000_000L, stats.getTotalBytesLoaded());
        assertEquals(750_000L, stats.getCacheHits());
        assertEquals(0.75, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testCacheHitsGreaterThanClassesLoaded() {
        // Edge case: more cache hits than classes loaded (shouldn't happen in practice)
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            50,
            1_000_000L,
            100L
        );

        assertEquals(2.0, stats.getCacheHitRate(), 0.001);
    }

    @Test
    void testZeroBytes() {
        ClassLoaderStatistics stats = new ClassLoaderStatistics(
            "loader",
            10,
            0L,
            5L
        );

        assertEquals(0L, stats.getTotalBytesLoaded());
        assertEquals(0.5, stats.getCacheHitRate(), 0.001);
    }
}
