package org.flossware.jclassloader.example;

import org.flossware.jclassloader.AuthConfig;
import org.flossware.jclassloader.ClassSource;
import org.flossware.jclassloader.JClassLoader;
import org.flossware.jclassloader.MavenRepositoryClassSource;
import org.flossware.jclassloader.RestApiClassSource;
import org.flossware.jclassloader.SftpClassSource;
import org.flossware.jclassloader.WebDavClassSource;
import org.flossware.jclassloader.cache.FileSystemCache;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Examples for various protocol support.
 * Demonstrates SFTP, WebDAV, database, and REST API class loading.
 */
public class ProtocolExamples {

    public static void main(String[] args) throws Exception {
        sftpExample();
        webDavExample();
        restApiExample();
        mavenCentralExample();
        mavenRepositoryExample();
        multiProtocolExample();
    }

    public static void sftpExample() {
        System.out.println("=== SFTP Class Loading Example ===");

        // SFTP with password authentication
        SftpClassSource sftpSource = SftpClassSource.builder()
            .host("sftp.example.com")
            .port(22)
            .username("deploy-user")
            .password("secret-password")
            .basePath("/var/classes")
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addClassSource(sftpSource)
            .build();

        System.out.println("SFTP ClassLoader created:");
        System.out.println("  " + sftpSource.getDescription());

        // SFTP with private key authentication
        SftpClassSource keySource = SftpClassSource.builder()
            .host("secure.example.com")
            .username("deploy-user")
            .privateKey("/home/user/.ssh/id_rsa")
            .basePath("/opt/app/classes")
            .build();

        System.out.println("\nSSH key-based authentication:");
        System.out.println("  " + keySource.getDescription());
        System.out.println();
    }

    public static void webDavExample() {
        System.out.println("=== WebDAV Class Loading Example ===");

        // Anonymous WebDAV
        JClassLoader publicLoader = JClassLoader.builder()
            .addWebDavSource("https://dav.example.com/classes/")
            .build();

        System.out.println("Public WebDAV source configured");

        // Authenticated WebDAV
        JClassLoader privateLoader = JClassLoader.builder()
            .addWebDavSource("https://secure-dav.company.com/classes/",
                           "username", "password")
            .build();

        System.out.println("Authenticated WebDAV source configured");
        System.out.println();
    }

    public static void restApiExample() {
        System.out.println("=== Generic REST API Example ===");

        // Custom REST API with specific headers and query parameters
        RestApiClassSource restSource = RestApiClassSource.builder()
            .baseUrl("https://api.example.com/classes")
            .classPathTemplate("v1/download/{fullclass}")
            .addHeader("X-API-Version", "2.0")
            .addHeader("Accept", "application/octet-stream")
            .addQueryParam("format", "binary")
            .auth(AuthConfig.bearer("api-token-12345"))
            .responseFormat(RestApiClassSource.ResponseFormat.BINARY)
            .build();

        JClassLoader loader = JClassLoader.builder()
            .addRestApiSource(restSource)
            .build();

        System.out.println("REST API ClassLoader configured:");
        System.out.println("  " + restSource.getDescription());

        // REST API returning Base64 JSON
        RestApiClassSource jsonSource = RestApiClassSource.builder()
            .baseUrl("https://api.example.com/artifacts")
            .classPathTemplate("classes/{fullclass}")
            .addHeader("Accept", "application/json")
            .auth(AuthConfig.basic("user", "pass"))
            .responseFormat(RestApiClassSource.ResponseFormat.BASE64_JSON_FIELD)
            .build();

        System.out.println("\nREST API with JSON response:");
        System.out.println("  " + jsonSource.getDescription());
        System.out.println();
    }

    public static void mavenCentralExample() {
        System.out.println("=== Maven Central Example ===");

        // Load from Maven Central
        JClassLoader loader = JClassLoader.builder()
            .addMavenCentral(
                "org.apache.commons:commons-lang3:3.12.0",
                "com.google.guava:guava:32.1.0-jre",
                "org.slf4j:slf4j-api:2.0.7"
            )
            .build();

        System.out.println("Maven Central ClassLoader created");
        System.out.println("Artifacts:");
        System.out.println("  - commons-lang3:3.12.0");
        System.out.println("  - guava:32.1.0-jre");
        System.out.println("  - slf4j-api:2.0.7");
        System.out.println("\nClasses can be loaded from these JARs:");
        System.out.println("  org.apache.commons.lang3.StringUtils");
        System.out.println("  com.google.common.collect.Lists");
        System.out.println("  org.slf4j.Logger");
        System.out.println();
    }

    public static void mavenRepositoryExample() {
        System.out.println("=== Custom Maven Repository Example ===");

        // JFrog Artifactory
        JClassLoader artifactory = JClassLoader.builder()
            .addMavenRepository(
                "https://artifactory.company.com/libs-release",
                "com.company:internal-lib:1.0.0",
                "com.company:shared-utils:2.3.0"
            )
            .build();

        System.out.println("JFrog Artifactory configured");

        // Using builder for more control
        MavenRepositoryClassSource customRepo = MavenRepositoryClassSource.builder()
            .repositoryUrl("https://maven.custom.com/repository/")
            .addArtifact("org.example:my-lib:1.0.0")
            .addArtifact("org.example", "another-lib", "2.0.0")
            .auth(AuthConfig.basic("maven-user", "maven-password"))
            .build();

        JClassLoader customLoader = JClassLoader.builder()
            .addClassSource(customRepo)
            .build();

        System.out.println("Custom Maven repository with authentication:");
        System.out.println("  " + customRepo.getDescription());
        System.out.println();
    }

    public static void multiProtocolExample() throws IOException {
        System.out.println("=== Multi-Protocol Enterprise Example ===");

        // Complex real-world scenario combining multiple protocols

        // 1. Local development overrides
        String localOverrides = "/opt/app/dev-overrides";

        // 2. SFTP for shared team classes
        SftpClassSource teamClasses = SftpClassSource.builder()
            .host("team-sftp.company.com")
            .username("build-agent")
            .privateKey("/var/secrets/build-key")
            .basePath("/shared/classes")
            .build();

        // 3. WebDAV for documentation and resources
        WebDavClassSource docsSource = new WebDavClassSource(
            "https://dav.company.com/resources/",
            "docs-reader", "docs-password"
        );

        // 4. Internal Maven artifacts
        MavenRepositoryClassSource internalMaven = MavenRepositoryClassSource.builder()
            .repositoryUrl("https://maven.company.com/internal/")
            .addArtifact("com.company:core:3.1.0")
            .addArtifact("com.company:api-client:2.5.0")
            .auth(AuthConfig.basic("ci-user", "ci-token"))
            .build();

        // 5. Maven Central fallback
        MavenRepositoryClassSource mavenCentral = MavenRepositoryClassSource.builder()
            .mavenCentral()
            .addArtifact("org.apache.commons:commons-lang3:3.12.0")
            .addArtifact("com.google.code.gson:gson:2.10.1")
            .build();

        // 6. Custom REST API for legacy system
        RestApiClassSource legacyApi = RestApiClassSource.builder()
            .baseUrl("https://legacy.company.com/classloader-api/")
            .classPathTemplate("fetch?class={fullclass}")
            .addHeader("X-Legacy-Token", "legacy-token-abc123")
            .responseFormat(RestApiClassSource.ResponseFormat.BINARY)
            .build();

        // Combine everything with caching
        FileSystemCache cache = new FileSystemCache("/var/cache/jclassloader");

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource(localOverrides)           // 1. Local first
            .addClassSource(teamClasses)              // 2. Team SFTP
            .addClassSource(docsSource)               // 3. WebDAV docs
            .addClassSource(internalMaven)            // 4. Internal Maven
            .addClassSource(mavenCentral)             // 5. Maven Central
            .addRestApiSource(legacyApi)              // 6. Legacy REST API
            .cache(cache)
            .useCache(true)
            .build();

        System.out.println("Enterprise multi-protocol ClassLoader:");
        System.out.println("Resolution order:");
        int i = 1;
        for (ClassSource source : loader.getClassSources()) {
            System.out.println("  " + i++ + ". " + source.getDescription());
        }
        System.out.println("\nCaching: " + cache.getCacheDirectory());
        System.out.println();
    }

    public static void databaseExample() throws Exception {
        System.out.println("=== Database Class Loading Example ===");

        // Note: This requires a DataSource configured with a JDBC driver
        // For demo purposes, showing the pattern

        System.out.println("Database ClassSource pattern:");
        System.out.println("  - Classes stored as BLOBs in database");
        System.out.println("  - Table: class_storage");
        System.out.println("  - Columns: class_name (VARCHAR), class_bytes (BLOB)");
        System.out.println("\nUsage:");
        System.out.println("  DataSource ds = ... // configure your DataSource");
        System.out.println("  JClassLoader loader = JClassLoader.builder()");
        System.out.println("      .addDatabaseSource(ds, \"class_storage\",");
        System.out.println("                        \"class_name\", \"class_bytes\")");
        System.out.println("      .build();");
        System.out.println();
    }
}
