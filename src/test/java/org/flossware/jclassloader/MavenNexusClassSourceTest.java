package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MavenNexusClassSourceTest {

    @Test
    void testConstructorRequiresArtifacts() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MavenNexusClassSource("https://nexus.example.com", "releases", Collections.emptyList());
        });
    }

    @Test
    void testBuilderBasic() {
        MavenNexusClassSource source = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.example.com")
            .repository("releases")
            .addArtifact("org.example:my-lib:1.0.0")
            .build();

        assertNotNull(source);
        assertEquals("https://nexus.example.com/", source.getNexusUrl());
        assertEquals("releases", source.getRepository());
        assertEquals(1, source.getArtifacts().size());
    }

    @Test
    void testBuilderMultipleArtifacts() {
        MavenNexusClassSource source = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.example.com")
            .repository("releases")
            .addArtifact("org.example:lib1:1.0.0")
            .addArtifact("org.example", "lib2", "2.0.0")
            .addArtifact(new MavenArtifact("org.example", "lib3", "3.0.0"))
            .build();

        assertEquals(3, source.getArtifacts().size());
    }

    @Test
    void testBuilderWithAuth() {
        AuthConfig auth = AuthConfig.basic("user", "pass");
        MavenNexusClassSource source = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.example.com")
            .repository("private-repo")
            .addArtifact("org.example:my-lib:1.0.0")
            .auth(auth)
            .build();

        assertEquals(AuthConfig.AuthType.BASIC, source.getAuthConfig().getAuthType());
    }

    @Test
    void testBuilderRequiresNexusUrl() {
        assertThrows(NullPointerException.class, () -> {
            MavenNexusClassSource.builder()
                .repository("releases")
                .addArtifact("org.example:my-lib:1.0.0")
                .build();
        });
    }

    @Test
    void testBuilderRequiresRepository() {
        assertThrows(NullPointerException.class, () -> {
            MavenNexusClassSource.builder()
                .nexusUrl("https://nexus.example.com")
                .addArtifact("org.example:my-lib:1.0.0")
                .build();
        });
    }

    @Test
    void testGetDescription() {
        MavenNexusClassSource source = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.example.com")
            .repository("releases")
            .addArtifact("org.example:my-lib:1.0.0")
            .build();

        String description = source.getDescription();
        assertTrue(description.contains("nexus.example.com"));
        assertTrue(description.contains("releases"));
        assertTrue(description.contains("artifacts=1"));
    }

    @Test
    void testAddArtifactAfterCreation() {
        MavenArtifact artifact = new MavenArtifact("org.example", "my-lib", "1.0.0");
        MavenNexusClassSource source = new MavenNexusClassSource(
            "https://nexus.example.com",
            "releases",
            Arrays.asList(artifact)
        );

        assertEquals(1, source.getArtifacts().size());

        source.addArtifact("org.example:another-lib:2.0.0");
        assertEquals(2, source.getArtifacts().size());
    }
}
