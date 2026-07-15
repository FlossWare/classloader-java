# classloader-java

A Java ClassLoader that loads classes from local directories, HTTP/HTTPS servers, Maven repositories, databases, object stores, and more. Supports caching, authentication, bytecode verification, configurable delegation, and lifecycle listeners.

**Java 11+ | GPLv3 | Maven Central: `org.flossware:classloader-java:2.0`**

## Quick Start

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>classloader-java</artifactId>
    <version>2.0</version>
</dependency>
```

```java
try (ApplicationClassLoader loader = ApplicationClassLoader.builder()
        .addLocalSource("/opt/app/classes")
        .addRemoteSource("https://cdn.example.com/classes")
        .addMavenCentral("com.google.guava:guava:32.1.0-jre")
        .build()) {
    Class<?> clazz = loader.loadClass("com.example.MyClass");
}
```

## Class Sources

| Source | Class | Use Case |
|--------|-------|----------|
| Local filesystem | `LocalClassSource` | Load `.class` files from a directory |
| HTTP/HTTPS | `RemoteClassSource` | Download classes from a web server |
| Remote JAR | `RemoteJarClassSource` | Download and extract a JAR |
| Maven repository | `MavenRepositoryClassSource` | Load from Maven Central or private repos |
| Nexus repository | `NexusClassSource` | Load from Sonatype Nexus (RAW or Maven) |
| JDBC database | `DatabaseClassSource` | Load bytecode stored in a database table |
| REST API | `RestApiClassSource` | Load from a custom REST endpoint |
| MinIO / S3 | `MinioClassSource` | Load from S3-compatible object storage |
| HDFS | `HdfsClassSource` | Load from Hadoop Distributed File System |
| Custom protocol | `CustomProtocolClassSource` | Load via registered protocol handlers |
| Custom | Implement `ClassSource` | Any source you need |

### Extension Sources (optional)

These require the FlossWare extension libraries. Build with `-Pflossware-extensions` when these artifacts are in your local Maven repository.

| Source | Class | Dependency |
|--------|-------|------------|
| Cloud storage | `CloudStorageClassSource` | `org.flossware:cloudstorage-java` |
| File transfer (SFTP/FTP) | `FileTransferClassSource` | `org.flossware:filetransfer-java` |
| Message queue | `MessageClientClassSource` | `org.flossware:messaging-java` |
| Container registry | `ContainerClientClassSource` | `org.flossware:container-java` |
| VCS (Git) | `VcsClientClassSource` | `org.flossware:vcs-java` |

### Builder Shortcuts

```java
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    // Local
    .addLocalSource("/path/to/classes")

    // Remote with auth
    .addRemoteSource("https://secure.example.com/classes",
        AuthConfig.bearer("my-token"))

    // Remote JAR
    .addRemoteJar("https://cdn.example.com/lib-1.0.jar")

    // Maven Central
    .addMavenCentral("org.apache.commons:commons-lang3:3.12.0")

    // Custom Maven repo
    .addMavenRepository("https://nexus.example.com/repository/releases/",
        "com.internal:my-lib:2.0")

    // Nexus RAW
    .addNexusRawSource("https://nexus.example.com", "my-repo",
        AuthConfig.basic("user", "pass"))

    // Database
    .addDatabaseSource(dataSource, "class_files", "class_name", "bytecode")

    // Any ClassSource implementation
    .addClassSource(myCustomSource)

    .build();
```

### MinIO / S3

```java
MinioClassSource source = MinioClassSource.builder()
    .endpoint("minio.example.com")
    .accessKey("minioadmin")
    .secretKey("minioadmin")
    .bucket("classes")
    .prefix("production/v2")
    .build();
```

Works with any S3-compatible service (AWS S3, Backblaze B2, Cloudflare R2, DigitalOcean Spaces).

## Delegation Strategies

Controls whether the parent ClassLoader or custom sources are checked first.

```java
// Standard Java behavior (default)
builder.parentFirst()

// Check custom sources first (for class isolation / overriding)
builder.parentLast()

// Per-class control
builder.customDelegation(className ->
    className.startsWith("com.example.shared."))
```

`parentLast()` always delegates `java.*`, `javax.*`, `sun.*`, and `jdk.*` to the parent.

## Caching

Caching is enabled by default (in-memory). Use `FileSystemCache` for persistence across JVM restarts:

```java
builder.cache(new FileSystemCache(Paths.get("/tmp/class-cache")))
```

Disable caching:

```java
builder.useCache(false)
```

## Bytecode Verification

Validate loaded classes against known checksums:

```java
Map<String, String> checksums = Map.of(
    "com.example.MyClass", "sha256:abc123..."
);
builder.bytecodeVerifier(new ChecksumValidator(checksums))
```

## Lifecycle Listeners

Monitor class loading events:

```java
builder.addLoggingListener(true)   // Log load, cache, resource events
       .trackResources()           // Track loaded classes and bytes

// Custom listener
builder.addListener(new ClassLoaderLifecycleListener() {
    @Override
    public void onClassLoaded(ClassLoadEvent event) { /* ... */ }
})
```

## Authentication

```java
AuthConfig.none()                          // No auth
AuthConfig.basic("username", "password")   // HTTP Basic
AuthConfig.bearer("token")                 // Bearer token
```

## Thread Safety

`ApplicationClassLoader` is thread-safe. It uses a `ReentrantReadWriteLock` (fair) to protect `findClass()` and `findResource()`, and prevents class loading after `close()`.

## API Documentation

Generate Javadoc:

```bash
mvn javadoc:javadoc
```

Output: `target/site/apidocs/index.html`

## Building

```bash
mvn clean install
```

Optional FlossWare extensions (cloud storage, file transfer, messaging, container, VCS):

```bash
mvn clean install -Pflossware-extensions
```

## License

[GNU General Public License v3.0](LICENSE)
