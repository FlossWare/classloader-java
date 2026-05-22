package org.flossware.jclassloader.example;

import org.flossware.jclassloader.AuthConfig;
import org.flossware.jclassloader.JClassLoader;
import org.flossware.jclassloader.MavenArtifact;
import org.flossware.jclassloader.MavenNexusClassSource;
import org.flossware.jclassloader.cache.FileSystemCache;

import java.io.IOException;

/**
 * Nexus repository usage examples.
 * Demonstrates loading classes from Nexus RAW and Maven repositories with authentication.
 */
public class NexusExample {

    public static void main(String[] args) throws Exception {
        nexusRawRepositoryExample();
        nexusMavenRepositoryExample();
        nexusWithAuthenticationExample();
        mavenArtifactExample();
        nexusWithCachingExample();
    }

    public static void nexusRawRepositoryExample() {
        System.out.println("=== Nexus Raw Repository Example ===");

        JClassLoader loader = JClassLoader.builder()
            .addNexusRawSource("https://nexus.example.com", "raw-classes")
            .build();

        System.out.println("ClassLoader created with Nexus raw repository");
        System.out.println("Nexus URL: https://nexus.example.com");
        System.out.println("Repository: raw-classes");
        System.out.println();
    }

    public static void nexusMavenRepositoryExample() {
        System.out.println("=== Nexus Maven Repository Example ===");

        MavenNexusClassSource nexusSource = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.example.com")
            .repository("maven-releases")
            .addArtifact("org.apache.commons:commons-lang3:3.12.0")
            .addArtifact("com.google.guava:guava:32.1.0-jre")
            .addArtifact("org.json:json:20230227")
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addNexusMavenSource(nexusSource)
            .build();

        System.out.println("ClassLoader created with Nexus Maven repository");
        System.out.println("Configured artifacts:");
        for (MavenArtifact artifact : nexusSource.getArtifacts()) {
            System.out.println("  - " + artifact);
            System.out.println("    Path: " + artifact.toPath());
        }
        System.out.println();
    }

    public static void nexusWithAuthenticationExample() {
        System.out.println("=== Nexus with Authentication Example ===");

        // Basic Authentication
        AuthConfig basicAuth = AuthConfig.basic("nexus-user", "nexus-password");

        MavenNexusClassSource privateSource = MavenNexusClassSource.builder()
            .nexusUrl("https://private-nexus.company.com")
            .repository("private-releases")
            .addArtifact("com.company:internal-api:1.0.0")
            .addArtifact("com.company:shared-models:2.3.1")
            .auth(basicAuth)
            .build();

        System.out.println("Created authenticated Nexus source:");
        System.out.println("  URL: " + privateSource.getNexusUrl());
        System.out.println("  Repository: " + privateSource.getRepository());
        System.out.println("  Auth Type: " + privateSource.getAuthConfig().getAuthType());
        System.out.println("  Artifacts: " + privateSource.getArtifacts().size());

        // Bearer Token Authentication (Nexus 3)
        AuthConfig tokenAuth = AuthConfig.bearer("NexusToken_abc123xyz");

        JClassLoader loader = JClassLoader.builder()
            .addNexusRawSource("https://nexus3.example.com", "npm-raw", tokenAuth)
            .build();

        System.out.println("\nCreated token-authenticated raw source");
        System.out.println();
    }

    public static void mavenArtifactExample() {
        System.out.println("=== Maven Artifact Parsing Example ===");

        // Parse different formats
        MavenArtifact simple = MavenArtifact.parse("org.example:my-lib:1.0.0");
        System.out.println("Simple artifact: " + simple);
        System.out.println("  Group ID: " + simple.getGroupId());
        System.out.println("  Artifact ID: " + simple.getArtifactId());
        System.out.println("  Version: " + simple.getVersion());
        System.out.println("  Path: " + simple.toPath());

        MavenArtifact withClassifier = MavenArtifact.parse("org.example:my-lib:1.0.0:sources");
        System.out.println("\nWith classifier: " + withClassifier);
        System.out.println("  Classifier: " + withClassifier.getClassifier());
        System.out.println("  Path: " + withClassifier.toPath());

        MavenArtifact full = MavenArtifact.parse("org.example:my-lib:1.0.0:javadoc:zip");
        System.out.println("\nFull coordinates: " + full);
        System.out.println("  Packaging: " + full.getPackaging());
        System.out.println("  Path: " + full.toPath());
        System.out.println();
    }

    public static void nexusWithCachingExample() throws IOException {
        System.out.println("=== Nexus with Caching Example ===");

        FileSystemCache cache = new FileSystemCache("/tmp/nexus-class-cache");

        MavenNexusClassSource nexusSource = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.example.com")
            .repository("maven-central")
            .addArtifact("org.slf4j:slf4j-api:2.0.7")
            .addArtifact("ch.qos.logback:logback-classic:1.4.8")
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addNexusMavenSource(nexusSource)
            .cache(cache)
            .useCache(true)
            .build();

        System.out.println("ClassLoader with caching configured");
        System.out.println("Cache directory: " + cache.getCacheDirectory());
        System.out.println("Cache enabled: " + loader.isCacheEnabled());
        System.out.println("\nBenefit: Classes downloaded from Nexus JARs are cached locally");
        System.out.println("Subsequent loads are faster and don't require network access");
        System.out.println();
    }

    public static void realWorldExample() throws IOException {
        System.out.println("=== Real-World Multi-Source Example ===");

        // Corporate Nexus with internal libraries
        AuthConfig nexusAuth = AuthConfig.basic("build-user", "build-password");
        MavenNexusClassSource internalLibs = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.company.com")
            .repository("internal-releases")
            .addArtifact("com.company:core-models:3.1.0")
            .addArtifact("com.company:api-client:2.5.3")
            .auth(nexusAuth)
            .build();

        // Public Maven Central mirror in Nexus (no auth)
        MavenNexusClassSource publicLibs = MavenNexusClassSource.builder()
            .nexusUrl("https://nexus.company.com")
            .repository("maven-central")
            .addArtifact("org.apache.commons:commons-lang3:3.12.0")
            .addArtifact("com.google.code.gson:gson:2.10.1")
            .build();

        // Local override directory for development
        String localOverrides = "/opt/app/local-overrides";

        // Combine all sources with caching
        FileSystemCache cache = new FileSystemCache("/var/cache/app-classes");

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource(localOverrides)          // Check local first
            .addNexusMavenSource(internalLibs)       // Then internal Nexus
            .addNexusMavenSource(publicLibs)         // Then public Nexus mirror
            .cache(cache)
            .useCache(true)
            .build();

        System.out.println("Multi-source ClassLoader configured:");
        System.out.println("1. Local overrides: " + localOverrides);
        System.out.println("2. Internal Nexus: " + internalLibs.getArtifacts().size() + " artifacts");
        System.out.println("3. Public Nexus: " + publicLibs.getArtifacts().size() + " artifacts");
        System.out.println("4. File cache: " + cache.getCacheDirectory());
        System.out.println("\nClass resolution order:");
        System.out.println("  Local → Internal Nexus → Public Nexus → Cache");
        System.out.println();
    }
}
