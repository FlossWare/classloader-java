package org.flossware.classloader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for MavenRepositoryClassSource builder and configuration.
 */
class MavenRepositoryClassSourceTest {

    @Test
    void testConstructorWithFullConfiguration() {
        List<MavenArtifact> artifacts = Arrays.asList(
                new MavenArtifact("org.example", "lib1", "1.0.0"),
                new MavenArtifact("org.example", "lib2", "2.0.0")
        );
        AuthConfig auth = AuthConfig.basic("user", "pass");

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com/maven2", artifacts, auth);

        assertEquals("https://repo.example.com/maven2/", source.getRepositoryUrl());
        assertEquals(2, source.getArtifacts().size());
    }

    @Test
    void testConstructorWithoutAuth() {
        List<MavenArtifact> artifacts = Arrays.asList(
                new MavenArtifact("org.example", "lib", "1.0.0")
        );

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com/maven2", artifacts);

        assertEquals("https://repo.example.com/maven2/", source.getRepositoryUrl());
    }

    @Test
    void testConstructorAddsTrailingSlash() {
        List<MavenArtifact> artifacts = Arrays.asList(
                new MavenArtifact("org.example", "lib", "1.0.0")
        );

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com/maven2", artifacts);

        assertTrue(source.getRepositoryUrl().endsWith("/"));
    }

    @Test
    void testConstructorNullUrlThrowsException() {
        List<MavenArtifact> artifacts = Arrays.asList(
                new MavenArtifact("org.example", "lib", "1.0.0")
        );

        assertThrows(NullPointerException.class, () -> {
            new MavenRepositoryClassSource(null, artifacts);
        });
    }

    @Test
    void testConstructorNullArtifactsThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            new MavenRepositoryClassSource("https://repo.example.com", null);
        });
    }

    @Test
    void testConstructorEmptyArtifactsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MavenRepositoryClassSource("https://repo.example.com", new ArrayList<>());
        });
    }

    @Test
    void testAddArtifact() {
        List<MavenArtifact> artifacts = new ArrayList<>();
        artifacts.add(new MavenArtifact("org.example", "lib1", "1.0.0"));

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com", artifacts);

        assertEquals(1, source.getArtifacts().size());

        source.addArtifact(new MavenArtifact("org.example", "lib2", "2.0.0"));

        assertEquals(2, source.getArtifacts().size());
    }

    @Test
    void testAddArtifactFromCoordinates() {
        List<MavenArtifact> artifacts = new ArrayList<>();
        artifacts.add(new MavenArtifact("org.example", "lib1", "1.0.0"));

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com", artifacts);

        source.addArtifact("org.example:lib2:2.0.0");

        assertEquals(2, source.getArtifacts().size());
    }

    @Test
    void testAddArtifactNullThrowsException() {
        List<MavenArtifact> artifacts = Arrays.asList(
                new MavenArtifact("org.example", "lib", "1.0.0")
        );

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com", artifacts);

        assertThrows(NullPointerException.class, () -> {
            source.addArtifact((MavenArtifact) null);
        });
    }

    @Test
    void testGetArtifactsReturnsNewList() {
        List<MavenArtifact> artifacts = new ArrayList<>();
        artifacts.add(new MavenArtifact("org.example", "lib", "1.0.0"));

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com", artifacts);

        List<MavenArtifact> retrieved1 = source.getArtifacts();
        List<MavenArtifact> retrieved2 = source.getArtifacts();

        assertNotSame(retrieved1, retrieved2);
    }

    @Test
    void testGetDescription() {
        List<MavenArtifact> artifacts = Arrays.asList(
                new MavenArtifact("org.example", "lib1", "1.0.0"),
                new MavenArtifact("org.example", "lib2", "2.0.0")
        );

        MavenRepositoryClassSource source = new MavenRepositoryClassSource(
                "https://repo.example.com/maven2", artifacts);

        String description = source.getDescription();
        assertTrue(description.contains("MavenRepositoryClassSource"));
        assertTrue(description.contains("https://repo.example.com/maven2/"));
        assertTrue(description.contains("artifacts=2"));
        assertTrue(description.contains("auth=NONE"));
    }

    @Test
    void testBuilderRepositoryUrl() {
        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .repositoryUrl("https://custom.repo.com/maven")
                .addArtifact("org.example:lib:1.0.0")
                .build();

        assertEquals("https://custom.repo.com/maven/", source.getRepositoryUrl());
    }

    @Test
    void testBuilderMavenCentral() {
        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .mavenCentral()
                .addArtifact("org.example:lib:1.0.0")
                .build();

        assertEquals("https://repo1.maven.org/maven2/", source.getRepositoryUrl());
    }

    @Test
    void testBuilderAddArtifactObject() {
        MavenArtifact artifact = new MavenArtifact("org.example", "lib", "1.0.0");

        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .repositoryUrl("https://repo.example.com")
                .addArtifact(artifact)
                .build();

        assertEquals(1, source.getArtifacts().size());
    }

    @Test
    void testBuilderAddArtifactWithGroupArtifactVersion() {
        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .repositoryUrl("https://repo.example.com")
                .addArtifact("org.example", "lib", "1.0.0")
                .build();

        assertEquals(1, source.getArtifacts().size());
    }

    @Test
    void testBuilderAddArtifactWithCoordinates() {
        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .repositoryUrl("https://repo.example.com")
                .addArtifact("org.example:lib:1.0.0")
                .build();

        assertEquals(1, source.getArtifacts().size());
    }

    @Test
    void testBuilderAuth() {
        AuthConfig auth = AuthConfig.basic("user", "pass");

        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .repositoryUrl("https://repo.example.com")
                .addArtifact("org.example:lib:1.0.0")
                .auth(auth)
                .build();

        assertNotNull(source);
    }

    @Test
    void testBuilderNullRepositoryUrlThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            MavenRepositoryClassSource.builder()
                    .addArtifact("org.example:lib:1.0.0")
                    .build();
        });
    }

    @Test
    void testBuilderNoArtifactsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            MavenRepositoryClassSource.builder()
                    .repositoryUrl("https://repo.example.com")
                    .build();
        });
    }

    @Test
    void testBuilderNullArtifactThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            MavenRepositoryClassSource.builder()
                    .repositoryUrl("https://repo.example.com")
                    .addArtifact((MavenArtifact) null)
                    .build();
        });
    }

    @Test
    void testConstants() {
        assertEquals("https://repo1.maven.org/maven2/", MavenRepositoryClassSource.MAVEN_CENTRAL);
        assertEquals("https://jcenter.bintray.com/", MavenRepositoryClassSource.JCENTER);
        assertEquals("https://maven.google.com/", MavenRepositoryClassSource.GOOGLE);
    }

    @Test
    void testBuilderChaining() {
        MavenRepositoryClassSource source = MavenRepositoryClassSource.builder()
                .mavenCentral()
                .addArtifact("org.example:lib1:1.0.0")
                .addArtifact("org.example:lib2:2.0.0")
                .addArtifact("org.example", "lib3", "3.0.0")
                .auth(AuthConfig.bearer("token"))
                .build();

        assertEquals(3, source.getArtifacts().size());
    }
}
