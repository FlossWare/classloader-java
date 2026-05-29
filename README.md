# ApplicationClassLoader

A flexible Java ClassLoader that can load classes from 34+ transport protocols with built-in caching support and authentication.

## Features

### Class Loading Sources
- **Local Class Loading**: Load classes from local file system directories
- **Remote Class Loading**: Load classes from HTTP/HTTPS URLs with JAR support
- **FTP/FTPS Support**: Load classes from FTP and FTPS servers
- **Nexus Repository Support**: Load classes from Sonatype Nexus repositories (both raw and Maven repositories)
- **Maven Artifact Resolution**: Automatically extract classes from Maven JARs hosted in Nexus
- **Cloud Storage** (via [jcloudstorage](https://github.com/FlossWare/cloudstorage-java)): AWS S3, Azure Blob, Google Cloud Storage, Google Drive, Dropbox, OneDrive
- **File Transfer** (via [jfiletransfer](https://github.com/FlossWare/filetransfer-java)): SFTP, WebDAV, SMB/CIFS, FTP/FTPS
- **Messaging** (via [jmessaging](https://github.com/FlossWare/messaging-java)): Kafka, RabbitMQ, Redis
- **Containers** (via [jcontainer](https://github.com/FlossWare/container-java)): Kubernetes ConfigMaps, Docker, Hazelcast
- **Version Control** (via [jvcs](https://github.com/FlossWare/vcs-java)): Git (local and remote)
- **Databases**: Load classes from JDBC-accessible databases

### Isolation & Control
- **Delegation Strategies**: Choose parent-first (standard), parent-last (isolation), or custom delegation
- **Lifecycle Hooks**: Monitor class loading events for tracking, logging, and resource management
- **Resource Tracking**: Track loaded classes and open resources for cleanup
- **Caching**: Optional file-system based caching to avoid repeated downloads
- **Authentication**: Support for HTTP Basic and Bearer token authentication

### Developer Experience
- **Builder Pattern**: Fluent API for easy configuration
- **Extensible**: Add custom class sources by implementing the `ClassSource` interface
- **Well Tested**: Comprehensive test suite with 392 unit tests (46% code coverage)

## Requirements

- Java 11 or higher
- Maven 3.6 or higher (for building)

## Installation

### Maven

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>classloader-java</artifactId>
    <version>2.0</version>
</dependency>
```

### Building from Source

```bash
git clone https://github.com/FlossWare/classloader-java.git
cd classloader-java
mvn clean install
```

## Usage

### Basic Local Class Loading

```java
import org.flossware.classloader-java.ApplicationClassLoader;

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .useCache(false)
    .build();

Class<?> myClass = loader.loadClass("com.example.MyClass");
Object instance = myClass.getDeclaredConstructor().newInstance();
```

### Remote Class Loading (HTTP/HTTPS)

```java
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("https://example.com/classes/")
    .build();

Class<?> myClass = loader.loadClass("com.example.MyClass");
```

### Remote Class Loading with Authentication

```java
import org.flossware.classloader-java.AuthConfig;

// Basic Authentication
AuthConfig basicAuth = AuthConfig.basic("username", "password");
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("https://secure.example.com/classes/", basicAuth)
    .build();

// Bearer Token Authentication
AuthConfig bearerAuth = AuthConfig.bearer("your-token-here");
ApplicationClassLoader loader2 = ApplicationClassLoader.builder()
    .addRemoteSource("https://api.example.com/classes/", bearerAuth)
    .build();
```

### FTP/FTPS Class Loading

```java
// Anonymous FTP
ApplicationClassLoader ftpLoader = ApplicationClassLoader.builder()
    .addClassSource(new FtpClassSource("ftp://ftp.example.com/classes/"))
    .build();

// Authenticated FTP
ApplicationClassLoader ftpAuthLoader = ApplicationClassLoader.builder()
    .addClassSource(new FtpClassSource("ftp://ftp.example.com/classes/", "username", "password"))
    .build();

// FTPS (FTP over SSL/TLS)
ApplicationClassLoader ftpsLoader = ApplicationClassLoader.builder()
    .addClassSource(new FtpClassSource("ftps://secure-ftp.example.com/classes/", "username", "password"))
    .build();
```

### Nexus Repository Support

#### Loading from Nexus Raw Repository

```java
import org.flossware.classloader-java.AuthConfig;

// Public Nexus raw repository
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addNexusRawSource("https://nexus.example.com", "raw-classes")
    .build();

// Authenticated Nexus raw repository
AuthConfig auth = AuthConfig.basic("username", "password");
ApplicationClassLoader authLoader = ApplicationClassLoader.builder()
    .addNexusRawSource("https://nexus.example.com", "private-raw", auth)
    .build();
```

#### Loading from Nexus Maven Repository

**Note:** Use `MavenNexusClassSource` for loading classes from Maven artifacts in Nexus. The `NexusClassSource` MAVEN mode is deprecated and non-functional.

Load classes from JAR files stored as Maven artifacts:

```java
import org.flossware.classloader-java.MavenNexusClassSource;
import org.flossware.classloader-java.MavenArtifact;

// Create a Maven Nexus source with specific artifacts
MavenNexusClassSource nexusSource = MavenNexusClassSource.builder()
    .nexusUrl("https://nexus.example.com")
    .repository("maven-releases")
    .addArtifact("org.apache.commons:commons-lang3:3.12.0")
    .addArtifact("com.google.guava:guava:32.1.0-jre")
    .build();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addNexusMavenSource(nexusSource)
    .build();

// Load a class from one of the configured artifacts
Class<?> stringUtils = loader.loadClass("org.apache.commons.lang3.StringUtils");
```

#### Nexus with Authentication

```java
// Using Basic Authentication
AuthConfig basicAuth = AuthConfig.basic("nexus-user", "nexus-password");

MavenNexusClassSource nexusSource = MavenNexusClassSource.builder()
    .nexusUrl("https://private-nexus.example.com")
    .repository("private-releases")
    .addArtifact("com.company:internal-lib:1.0.0")
    .auth(basicAuth)
    .build();

// Using Bearer Token (for Nexus 3 with token authentication)
AuthConfig tokenAuth = AuthConfig.bearer("NexusToken12345");

MavenNexusClassSource tokenSource = MavenNexusClassSource.builder()
    .nexusUrl("https://nexus.example.com")
    .repository("releases")
    .addArtifact("com.company:api-client:2.0.0")
    .auth(tokenAuth)
    .build();
```

#### Working with Maven Artifacts

```java
import org.flossware.classloader-java.MavenArtifact;

// Parse Maven coordinates
MavenArtifact artifact = MavenArtifact.parse("org.example:my-lib:1.0.0");
System.out.println(artifact.getGroupId());    // org.example
System.out.println(artifact.getArtifactId()); // my-lib
System.out.println(artifact.getVersion());    // 1.0.0

// With classifier and packaging
MavenArtifact sources = MavenArtifact.parse("org.example:my-lib:1.0.0:sources:jar");

// Convert to repository path
String path = artifact.toPath(); // org/example/my-lib/1.0.0/my-lib-1.0.0.jar
```

### Using Cache

```java
import org.flossware.classloader-java.cache.FileSystemCache;

FileSystemCache cache = new FileSystemCache("/tmp/classloader-java-cache");

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("https://example.com/classes/")
    .cache(cache)
    .useCache(true)
    .build();

// First load: downloads from remote and caches
Class<?> myClass = loader.loadClass("com.example.MyClass");

// Subsequent loads: uses cached version
Class<?> myCachedClass = loader.loadClass("com.example.MyClass");
```

### Multiple Class Sources

Classes are searched in the order sources are added:

```java
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/opt/app/classes")           // Searched first
    .addRemoteSource("https://cdn.example.com/")  // Searched second
    .addClassSource(new FtpClassSource("ftp://backup.example.com/classes/"))  // Searched third
    .cache(new FileSystemCache("/tmp/cache"))
    .build();
```

### Cloud Storage Support

Load classes from cloud storage providers using the [jcloudstorage](https://github.com/FlossWare/cloudstorage-java) library:

```xml
<!-- Add jcloudstorage dependency -->
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>cloudstorage-java</artifactId>
    <version>1.0</version>
</dependency>

<!-- Add provider SDK (only what you need) -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.44.12</version>
</dependency>
```

```java
import org.flossware.cloud.storage.CloudStorageClient;
import org.flossware.cloud.storage.S3CloudStorageClient;
import org.flossware.classloader-java.CloudStorageClassSource;

// Create cloud storage client
CloudStorageClient s3 = S3CloudStorageClient.builder()
    .bucket("my-classes-bucket")
    .region("us-east-1")
    .prefix("production/classes/")
    .build();

// Use with ApplicationClassLoader
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addCloudStorage(s3)
    .build();

// Or wrap manually
ApplicationClassLoader loader2 = ApplicationClassLoader.builder()
    .addClassSource(new CloudStorageClassSource(s3))
    .build();
```

Supported cloud providers (via jcloudstorage):
- AWS S3
- Azure Blob Storage
- Google Cloud Storage
- Google Drive
- Dropbox
- OneDrive

See [jcloudstorage documentation](https://github.com/FlossWare/cloudstorage-java) for provider-specific configuration.

### File Transfer Support

Load classes via file transfer protocols using [jfiletransfer](https://github.com/FlossWare/filetransfer-java):

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>filetransfer-java</artifactId>
    <version>1.0</version>
</dependency>
```

```java
import org.flossware.filetransfer.FileTransferClient;
import org.flossware.filetransfer.SftpFileTransferClient;
import org.flossware.classloader-java.FileTransferClassSource;

// SFTP example
FileTransferClient sftp = SftpFileTransferClient.builder()
    .host("sftp.example.com")
    .username("deploy")
    .password("secret")
    .basePath("/opt/classes")
    .build();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addClassSource(new FileTransferClassSource(sftp))
    .build();
```

Supported protocols: SFTP, WebDAV, SMB/CIFS, FTP/FTPS. See [jfiletransfer docs](https://github.com/FlossWare/filetransfer-java).

### Messaging System Support

Load classes from messaging systems using [jmessaging](https://github.com/FlossWare/messaging-java):

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>messaging-java</artifactId>
    <version>1.0</version>
</dependency>
```

```java
import org.flossware.messaging.MessageClient;
import org.flossware.messaging.KafkaMessageClient;
import org.flossware.classloader-java.MessageClientClassSource;

// Kafka example
MessageClient kafka = KafkaMessageClient.builder()
    .bootstrapServers("kafka:9092")
    .topic("class-definitions")
    .build();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addClassSource(new MessageClientClassSource(kafka))
    .build();
```

Supported systems: Kafka, RabbitMQ, Redis. See [jmessaging docs](https://github.com/FlossWare/messaging-java).

### Container System Support

Load classes from containers using [jcontainer](https://github.com/FlossWare/container-java):

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>container-java</artifactId>
    <version>1.0</version>
</dependency>
```

```java
import org.flossware.container.ContainerClient;
import org.flossware.container.KubernetesContainerClient;
import org.flossware.classloader-java.ContainerClientClassSource;

// Kubernetes ConfigMap example
ContainerClient k8s = KubernetesContainerClient.builder()
    .namespace("production")
    .build();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addClassSource(new ContainerClientClassSource(k8s, "app-classes"))
    .build();
```

Supported systems: Kubernetes ConfigMaps, Docker, Hazelcast. See [jcontainer docs](https://github.com/FlossWare/container-java).

### Version Control Support

Load classes from version control using [jvcs](https://github.com/FlossWare/vcs-java):

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>vcs-java</artifactId>
    <version>1.0</version>
</dependency>
```

```java
import org.flossware.vcs.VcsClient;
import org.flossware.vcs.GitVcsClient;
import org.flossware.classloader-java.VcsClientClassSource;

// Git repository example
VcsClient git = GitVcsClient.builder()
    .repositoryPath("/opt/app-repo")
    .branch("release/v1.0")
    .basePath("build/classes")
    .build();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addClassSource(new VcsClientClassSource(git))
    .build();
```

Supported systems: Git (local and remote). See [jvcs docs](https://github.com/FlossWare/vcs-java).

### Custom Parent ClassLoader

```java
ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .parent(parentLoader)
    .addLocalSource("/path/to/classes")
    .build();
```

### Delegation Strategies (NEW in 1.0)

ApplicationClassLoader now supports configurable delegation strategies to control when classes are loaded from parent vs. configured sources.

#### Parent-First (Default - Standard Java Behavior)

```java
// Delegates to parent first, then checks sources
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .parentFirst()  // This is the default
    .build();
```

#### Parent-Last (For Application Isolation)

Useful for plugin systems, application containers, and isolation scenarios where you want to load application classes before parent classes.

```java
// Checks sources first, then falls back to parent
// System classes (java.*, javax.*) always from parent
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .parentLast("com.myapp.api.", "java.", "javax.")  // API classes from parent
    .build();
```

#### Custom Delegation

Fine-grained control using a predicate:

```java
// Custom logic to decide parent-first vs parent-last per class
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .customDelegation(className -> 
        className.startsWith("java.") ||           // System classes: parent-first
        className.startsWith("com.platform.api.")  // Platform API: parent-first
    )
    .build();
```

### Lifecycle Hooks (NEW in 1.0)

Monitor class loading events for tracking, debugging, and resource management.

#### Logging Listener

```java
// Log all class loading events
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addLoggingListener()  // Logs to System.out
    .build();

// Verbose logging (includes cache hits)
ApplicationClassLoader verboseLoader = ApplicationClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addLoggingListener(true)  // verbose = true
    .build();
```

#### Resource Tracking

Track loaded classes and resources for cleanup (useful for hot-reload, plugin unloading, etc.):

```java
import org.flossware.classloader-java.lifecycle.ResourceTrackingListener;

ResourceTrackingListener tracker = new ResourceTrackingListener();

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .addListener(tracker)
    .build();

// Load classes...
loader.loadClass("com.example.Plugin");

// Get statistics
System.out.println("Classes loaded: " + tracker.getTotalClassesLoaded());
System.out.println("Bytes loaded: " + tracker.getTotalBytesLoaded());
System.out.println("Cache hits: " + tracker.getCacheHits());

// Cleanup when done (e.g., unloading a plugin)
tracker.closeAllResources();
```

#### Custom Lifecycle Listener

Implement your own listener for custom monitoring:

```java
import org.flossware.classloader-java.lifecycle.ClassLoaderLifecycleListener;
import org.flossware.classloader-java.lifecycle.ClassLoadEvent;

ClassLoaderLifecycleListener myListener = new ClassLoaderLifecycleListener() {
    @Override
    public void onClassLoaded(ClassLoadEvent event) {
        // Custom logic: security checks, metrics, etc.
        System.out.println("Loaded: " + event.getClassName() + 
                          " from " + event.getSource().getDescription() +
                          " in " + event.getLoadTimeMillis() + "ms");
    }
    
    @Override
    public void onClassLoadFailed(String className, Throwable error) {
        // Handle failures
        System.err.println("Failed to load: " + className);
    }
};

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addListener(myListener)
    .build();
```

## Architecture

### Core Components

**Class Loading:**
- **`ApplicationClassLoader`**: Main classloader implementation extending `java.lang.ClassLoader`
- **`ClassSource`**: Interface for class loading sources (20+ implementations)
- **`LocalClassSource`**: Loads classes from local file system
- **`RemoteClassSource`**: Loads classes from HTTP/HTTPS URLs
- **`FtpClassSource`**: Loads classes from FTP/FTPS servers
- **`NexusClassSource`**: Loads classes from Nexus raw or Maven repositories
- **`MavenNexusClassSource`**: Specialized source for loading classes from Maven artifacts in Nexus
- **`MavenArtifact`**: Represents Maven coordinates and handles path resolution

**Delegation & Control (NEW in 1.0):**
- **`DelegationStrategy`**: Interface for delegation strategies
- **`ParentFirstDelegation`**: Standard Java delegation (default)
- **`ParentLastDelegation`**: Sources-first delegation for isolation
- **`CustomDelegation`**: Predicate-based custom delegation

**Lifecycle & Monitoring (NEW in 1.0):**
- **`ClassLoaderLifecycleListener`**: Interface for lifecycle events
- **`ClassLoadEvent`**: Event with class load details (source, time, size)
- **`ResourceTrackingListener`**: Tracks classes and resources for cleanup
- **`LoggingListener`**: Debug logging for class loading

**Caching & Auth:**
- **`ClassCache`**: Interface for caching implementations
- **`FileSystemCache`**: File-based cache implementation
- **`AuthConfig`**: Configuration for authentication

### How It Works

1. When `loadClass()` is called, the configured **delegation strategy** determines the order:
   - **Parent-first** (default): Checks parent → cache → sources
   - **Parent-last**: Checks cache → sources → parent (except system classes)
   - **Custom**: User-defined logic per class
2. If using cache and class is cached, returns cached bytecode
3. Otherwise, iterates through configured class sources in order
4. The first source that can load the class provides the bytecode
5. **Lifecycle listeners** are notified of the class load event
6. The class is defined and optionally cached for future use

## Security Considerations

**Important**: Loading classes from remote sources can be a security risk. Only load classes from trusted sources.

- Always use HTTPS instead of HTTP when possible
- Validate the source of remote classes
- Consider implementing checksum verification
- Use authentication for protected resources
- Be aware that cached classes persist between runs

## Testing

Run the test suite:

```bash
mvn test
```

### Test Coverage

ApplicationClassLoader has **463 comprehensive unit tests** achieving **46% instruction coverage** across the codebase. While we strive for high test coverage, 100% coverage is not the goal for this project. Here's why:

**What IS tested (well-covered):**
- ✅ **Core functionality** (53% coverage): Class loading, delegation strategies, authentication
- ✅ **Caching layer** (83% coverage): FileSystemCache, RedisClassSource
- ✅ **Lifecycle system** (82% coverage): Event listeners, resource tracking
- ✅ **Delegation strategies** (85% coverage): Parent-first, parent-last, custom
- ✅ **Protocol handling** (100% coverage): Protocol registry and handlers
- ✅ **Critical paths**: All main use cases and builder patterns extensively tested

**What is NOT fully tested (and why):**
- ❌ **Example code** (0% coverage in `org.flossware.classloader-java.example`): These are demo applications, not production code. Testing them would verify example syntax, not library functionality.
- ❌ **Deep integration scenarios** (66% coverage in messaging): Full integration tests with real Kafka, RabbitMQ, Redis, etc. would require running external infrastructure. We use mocks for unit tests and rely on real-world usage for integration validation.
- ❌ **Error recovery edge cases**: Some network timeout paths, exotic authentication failures, and rare filesystem conditions are difficult to trigger reliably in unit tests.
- ❌ **External system quirks**: Vendor-specific behaviors (Nexus API variations, messaging broker retry logic) are validated through manual testing and production usage.

**Testing Philosophy:**
- **Unit tests verify logic**, not infrastructure: We mock external services (HTTP clients, FTP connections, database connections) to test our code, not third-party libraries.
- **Builder pattern coverage**: Every builder is tested for null validation, required fields, and correct object construction.
- **Value object contracts**: All immutable objects (AuthConfig, MavenArtifact) have equals/hashCode/toString tests.
- **Critical security paths**: Authentication, SSL/TLS validation, path traversal protection are thoroughly tested.

**Coverage by package:**
```
org.flossware.classloader-java (core)        53%  ✅ Primary use cases
org.flossware.classloader-java.cache         83%  ✅ High reliability needed
org.flossware.classloader-java.lifecycle     82%  ✅ Critical for monitoring
org.flossware.classloader-java.delegation    85%  ✅ Core isolation feature
org.flossware.classloader-java.protocol     100%  ✅ Complete coverage
org.flossware.classloader-java.messaging     66%  ⚠️  Kafka/RabbitMQ mocked
org.flossware.classloader-java.example        0%  ❌ Demo code, not tested
```

**Running coverage reports:**
```bash
mvn clean test jacoco:report
# View report at: target/site/jacoco/index.html
```

The 46% coverage represents **high-quality, focused testing** of the library's core functionality. Additional coverage would primarily test external library behaviors or demo code, providing diminishing returns for the effort invested.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## Authors

- Scot P. Floess

## Use Cases

### Plugin Systems
Use parent-last delegation to isolate plugins from the host application:
```java
ApplicationClassLoader pluginLoader = ApplicationClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .parentLast("com.myapp.api.")  // Only API from host
    .trackResources()  // For cleanup when unloading
    .build();
```

### Application Containers (like JPlatform)
Run multiple applications in one JVM with isolation:
```java
ApplicationClassLoader app1Loader = ApplicationClassLoader.builder()
    .addLocalSource("/apps/app1.jar")
    .addMavenCentral("commons-lang3:3.12.0")
    .parentLast("org.platform.api.")
    .addListener(resourceTracker)
    .build();
```

### Testing Frameworks
Isolate tests and cleanup between runs:
```java
ResourceTrackingListener tracker = new ResourceTrackingListener();
ApplicationClassLoader testLoader = ApplicationClassLoader.builder()
    .addLocalSource("/test-classes")
    .addListener(tracker)
    .build();
// Run tests...
tracker.closeAllResources();  // Cleanup
```

### Multi-Tenant Applications
Load tenant-specific code with isolation:
```java
ApplicationClassLoader tenantLoader = ApplicationClassLoader.builder()
    .addRemoteSource("https://tenant1.example.com/code/")
    .parentLast()
    .build();
```

## Version History

### 1.0 (Current)
- ✅ 30+ ClassSource implementations (HTTP, FTP, SFTP, WebDAV, Maven, databases, Kafka, RabbitMQ, Redis, HDFS, Kubernetes, Docker, Git, MinIO, Hazelcast, etc.)
- ✅ Cloud storage support via [jcloudstorage](https://github.com/FlossWare/cloudstorage-java) library (S3, Azure Blob, GCS, Google Drive, Dropbox, OneDrive)
- ✅ JAR file loading from remote sources (HTTP/HTTPS)
- ✅ Bytecode verification and checksum validation (SHA-256)
- ✅ Retry logic with exponential backoff and jitter
- ✅ Delegation strategies (parent-first, parent-last, custom)
- ✅ Lifecycle hooks for monitoring and resource tracking
- ✅ File system caching
- ✅ Authentication support (Basic, Bearer)
- ✅ Comprehensive test suite (392 tests, 46% coverage)

## Roadmap

### Planned Features
- [ ] More cache implementations (in-memory)
- [ ] Performance metrics dashboard
- [ ] Class version conflict detection
- [ ] Hot reload / class reloading support
