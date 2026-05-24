package org.flossware.jclassloader.filesystem;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HdfsClassSource builder and configuration.
 */
class HdfsClassSourceTest {

    @Test
    void testBuilderWithBasePath() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .basePath("/classes")
                .build();

        assertNotNull(source);
        assertTrue(source.getDescription().contains("/classes"));
    }

    @Test
    void testBuilderWithNameNodeUri() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .nameNodeUri("hdfs://localhost:9000")
                .basePath("/classes")
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderWithConfiguration() throws IOException {
        Configuration conf = new Configuration();
        HdfsClassSource source = HdfsClassSource.builder()
                .configuration(conf)
                .basePath("/classes")
                .build();

        assertNotNull(source);
    }

    @Test
    void testGetDescription() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .basePath("/data/classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("HdfsClassSource"));
        assertTrue(description.contains("/data/classes"));
    }

    @Test
    void testBuilderChaining() {
        HdfsClassSource.Builder builder = HdfsClassSource.builder();
        assertSame(builder, builder.basePath("/classes"));
        assertSame(builder, builder.nameNodeUri("hdfs://localhost:9000"));
        assertSame(builder, builder.configuration(new Configuration()));
    }

    @Test
    void testClose() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .basePath("/classes")
                .build();

        assertDoesNotThrow(() -> source.close());
    }

    @Test
    void testMultipleClose() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .basePath("/classes")
                .build();

        assertDoesNotThrow(() -> {
            source.close();
            source.close();
        });
    }

    @Test
    void testBuilderWithAllOptions() {
        Configuration conf = new Configuration();
        HdfsClassSource.Builder builder = HdfsClassSource.builder()
                .nameNodeUri("hdfs://namenode:9000")
                .basePath("/app/classes")
                .configuration(conf);

        assertNotNull(builder);
    }

    @Test
    void testMultipleInstances() throws IOException {
        HdfsClassSource source1 = HdfsClassSource.builder()
                .basePath("/classes1")
                .build();

        HdfsClassSource source2 = HdfsClassSource.builder()
                .basePath("/classes2")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderDefaultBasePath() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .build();

        assertTrue(source.getDescription().contains("/"));
    }

    @Test
    void testBuilderWithDeepPath() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .basePath("/data/app/production/classes")
                .build();

        assertTrue(source.getDescription().contains("/data/app/production/classes"));
    }

    @Test
    void testBuilderDefaultNameNodeUri() throws IOException {
        HdfsClassSource source = HdfsClassSource.builder()
                .basePath("/classes")
                .build();

        assertNotNull(source);
    }

}

