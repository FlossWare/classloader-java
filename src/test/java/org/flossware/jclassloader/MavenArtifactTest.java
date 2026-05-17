package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MavenArtifactTest {

    @Test
    void testBasicArtifact() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0");

        assertEquals("org.example", artifact.getGroupId());
        assertEquals("my-lib", artifact.getArtifactId());
        assertEquals("1.0.0", artifact.getVersion());
        assertNull(artifact.getClassifier());
        assertEquals("jar", artifact.getPackaging());
    }

    @Test
    void testArtifactWithClassifier() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0", "sources", "jar");

        assertEquals("sources", artifact.getClassifier());
        assertEquals("jar", artifact.getPackaging());
    }

    @Test
    void testParseBasicCoordinates() {
        MavenArtifact artifact = MavenArtifact.parse("org.example:my-lib:1.0.0");

        assertEquals("org.example", artifact.getGroupId());
        assertEquals("my-lib", artifact.getArtifactId());
        assertEquals("1.0.0", artifact.getVersion());
        assertNull(artifact.getClassifier());
        assertEquals("jar", artifact.getPackaging());
    }

    @Test
    void testParseWithClassifier() {
        MavenArtifact artifact = MavenArtifact.parse("org.example:my-lib:1.0.0:sources");

        assertEquals("org.example", artifact.getGroupId());
        assertEquals("my-lib", artifact.getArtifactId());
        assertEquals("1.0.0", artifact.getVersion());
        assertEquals("sources", artifact.getClassifier());
        assertEquals("jar", artifact.getPackaging());
    }

    @Test
    void testParseWithClassifierAndPackaging() {
        MavenArtifact artifact = MavenArtifact.parse("org.example:my-lib:1.0.0:javadoc:zip");

        assertEquals("org.example", artifact.getGroupId());
        assertEquals("my-lib", artifact.getArtifactId());
        assertEquals("1.0.0", artifact.getVersion());
        assertEquals("javadoc", artifact.getClassifier());
        assertEquals("zip", artifact.getPackaging());
    }

    @Test
    void testParseInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> {
            MavenArtifact.parse("invalid");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            MavenArtifact.parse("org.example:my-lib");
        });
    }

    @Test
    void testToPath() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0");
        assertEquals("org/example/my-lib/1.0.0/my-lib-1.0.0.jar", artifact.toPath());
    }

    @Test
    void testToPathWithClassifier() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0", "sources", "jar");
        assertEquals("org/example/my-lib/1.0.0/my-lib-1.0.0-sources.jar", artifact.toPath());
    }

    @Test
    void testToString() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0");
        assertEquals("org.example:my-lib:1.0.0", artifact.toString());
    }

    @Test
    void testToStringWithClassifier() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0", "sources", "jar");
        assertEquals("org.example:my-lib:1.0.0:sources", artifact.toString());
    }

    @Test
    void testEqualsAndHashCode() {
        MavenArtifact artifact1 = new MavenArtifact("org.example", "my-lib", "1.0.0");
        MavenArtifact artifact2 = new MavenArtifact("org.example", "my-lib", "1.0.0");
        MavenArtifact artifact3 = new MavenArtifact("org.example", "my-lib", "2.0.0");

        assertEquals(artifact1, artifact2);
        assertEquals(artifact1.hashCode(), artifact2.hashCode());
        assertNotEquals(artifact1, artifact3);
    }
}
