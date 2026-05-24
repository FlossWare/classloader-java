# JClassLoader

A flexible Java ClassLoader that can load classes from both local and remote locations (HTTP/HTTPS/FTP/FTPS) with built-in caching support and authentication.

## Features

### Class Loading Sources
- **Local Class Loading**: Load classes from local file system directories
- **Remote Class Loading**: Load classes from HTTP/HTTPS URLs
- **FTP/FTPS Support**: Load classes from FTP and FTPS servers
- **Nexus Repository Support**: Load classes from Sonatype Nexus repositories (both raw and Maven repositories)
- **Maven Artifact Resolution**: Automatically extract classes from Maven JARs hosted in Nexus
- **Cloud Storage**: Support for AWS S3, Azure Blob, Google Cloud Storage, Google Drive, Dropbox, OneDrive
- **Databases**: Load classes from JDBC-accessible databases
- **Messaging**: Load classes from Kafka, RabbitMQ, Redis
- **Containers**: Load classes from Docker and Kubernetes
- **Version Control**: Load classes from Git repositories
- **File Systems**: Support for SFTP, WebDAV, SMB/CIFS

### Isolation & Control
- **Delegation Strategies**: Choose parent-first (standard), parent-last (isolation), or custom delegation
- **Lifecycle Hooks**: Monitor class loading events for tracking, logging, and resource management
- **Resource Tracking**: Track loaded classes and open resources for cleanup
- **Caching**: Optional file-system based caching to avoid repeated downloads
- **Authentication**: Support for HTTP Basic and Bearer token authentication

### Developer Experience
- **Builder Pattern**: Fluent API for easy configuration
- **Extensible**: Add custom class sources by implementing the `ClassSource` interface
- **Well Tested**: Comprehensive test suite with 46+ unit tests

## Requirements

- Java 11 or higher
- Maven 3.6 or higher (for building)

## Installation

### Maven

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jclassloader</artifactId>
    <version>1.0</version>
</dependency>
```

### Building from Source

```bash
git clone https://github.com/FlossWare/jclassloader.git
cd jclassloader
mvn clean install
```

## Usage

### Basic Local Class Loading

```java
import org.flossware.jclassloader.JClassLoader;

JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .useCache(false)
    .build();

Class<?> myClass = loader.loadClass("com.example.MyClass");
Object instance = myClass.getDeclaredConstructor().newInstance();
```

### Remote Class Loading (HTTP/HTTPS)

```java
JClassLoader loader = JClassLoader.builder()
    .addRemoteSource("https://example.com/classes/")
    .build();

Class<?> myClass = loader.loadClass("com.example.MyClass");
```

### Remote Class Loading with Authentication

```java
import org.flossware.jclassloader.AuthConfig;

// Basic Authentication
AuthConfig basicAuth = AuthConfig.basic("username", "password");
JClassLoader loader = JClassLoader.builder()
    .addRemoteSource("https://secure.example.com/classes/", basicAuth)
    .build();

// Bearer Token Authentication
AuthConfig bearerAuth = AuthConfig.bearer("your-token-here");
JClassLoader loader2 = JClassLoader.builder()
    .addRemoteSource("https://api.example.com/classes/", bearerAuth)
    .build();
```

### FTP/FTPS Class Loading

```java
// Anonymous FTP
JClassLoader ftpLoader = JClassLoader.builder()
    .addClassSource(new FtpClassSource("ftp://ftp.example.com/classes/"))
    .build();

// Authenticated FTP
JClassLoader ftpAuthLoader = JClassLoader.builder()
    .addClassSource(new FtpClassSource("ftp://ftp.example.com/classes/", "username", "password"))
    .build();

// FTPS (FTP over SSL/TLS)
JClassLoader ftpsLoader = JClassLoader.builder()
    .addClassSource(new FtpClassSource("ftps://secure-ftp.example.com/classes/", "username", "password"))
    .build();
```

### Nexus Repository Support

#### Loading from Nexus Raw Repository

```java
import org.flossware.jclassloader.AuthConfig;

// Public Nexus raw repository
JClassLoader loader = JClassLoader.builder()
    .addNexusRawSource("https://nexus.example.com", "raw-classes")
    .build();

// Authenticated Nexus raw repository
AuthConfig auth = AuthConfig.basic("username", "password");
JClassLoader authLoader = JClassLoader.builder()
    .addNexusRawSource("https://nexus.example.com", "private-raw", auth)
    .build();
```

#### Loading from Nexus Maven Repository

Load classes from JAR files stored as Maven artifacts:

```java
import org.flossware.jclassloader.MavenNexusClassSource;
import org.flossware.jclassloader.MavenArtifact;

// Create a Maven Nexus source with specific artifacts
MavenNexusClassSource nexusSource = MavenNexusClassSource.builder()
    .nexusUrl("https://nexus.example.com")
    .repository("maven-releases")
    .addArtifact("org.apache.commons:commons-lang3:3.12.0")
    .addArtifact("com.google.guava:guava:32.1.0-jre")
    .build();

JClassLoader loader = JClassLoader.builder()
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
import org.flossware.jclassloader.MavenArtifact;

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
import org.flossware.jclassloader.cache.FileSystemCache;

FileSystemCache cache = new FileSystemCache("/tmp/jclassloader-cache");

JClassLoader loader = JClassLoader.builder()
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
JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/opt/app/classes")           // Searched first
    .addRemoteSource("https://cdn.example.com/")  // Searched second
    .addClassSource(new FtpClassSource("ftp://backup.example.com/classes/"))  // Searched third
    .cache(new FileSystemCache("/tmp/cache"))
    .build();
```

### Custom Parent ClassLoader

```java
ClassLoader parentLoader = Thread.currentThread().getContextClassLoader();

JClassLoader loader = JClassLoader.builder()
    .parent(parentLoader)
    .addLocalSource("/path/to/classes")
    .build();
```

### Delegation Strategies (NEW in 1.0)

JClassLoader now supports configurable delegation strategies to control when classes are loaded from parent vs. configured sources.

#### Parent-First (Default - Standard Java Behavior)

```java
// Delegates to parent first, then checks sources
JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .parentFirst()  // This is the default
    .build();
```

#### Parent-Last (For Application Isolation)

Useful for plugin systems, application containers, and isolation scenarios where you want to load application classes before parent classes.

```java
// Checks sources first, then falls back to parent
// System classes (java.*, javax.*) always from parent
JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .parentLast("com.myapp.api.", "java.", "javax.")  // API classes from parent
    .build();
```

#### Custom Delegation

Fine-grained control using a predicate:

```java
// Custom logic to decide parent-first vs parent-last per class
JClassLoader loader = JClassLoader.builder()
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
JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addLoggingListener()  // Logs to System.out
    .build();

// Verbose logging (includes cache hits)
JClassLoader verboseLoader = JClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addLoggingListener(true)  // verbose = true
    .build();
```

#### Resource Tracking

Track loaded classes and resources for cleanup (useful for hot-reload, plugin unloading, etc.):

```java
import org.flossware.jclassloader.lifecycle.ResourceTrackingListener;

ResourceTrackingListener tracker = new ResourceTrackingListener();

JClassLoader loader = JClassLoader.builder()
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
import org.flossware.jclassloader.lifecycle.ClassLoaderLifecycleListener;
import org.flossware.jclassloader.lifecycle.ClassLoadEvent;

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

JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addListener(myListener)
    .build();
```

## Architecture

### Core Components

**Class Loading:**
- **`JClassLoader`**: Main classloader implementation extending `java.lang.ClassLoader`
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
JClassLoader pluginLoader = JClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .parentLast("com.myapp.api.")  // Only API from host
    .trackResources()  // For cleanup when unloading
    .build();
```

### Application Containers (like JPlatform)
Run multiple applications in one JVM with isolation:
```java
JClassLoader app1Loader = JClassLoader.builder()
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
JClassLoader testLoader = JClassLoader.builder()
    .addLocalSource("/test-classes")
    .addListener(tracker)
    .build();
// Run tests...
tracker.closeAllResources();  // Cleanup
```

### Multi-Tenant Applications
Load tenant-specific code with isolation:
```java
JClassLoader tenantLoader = JClassLoader.builder()
    .addRemoteSource("https://tenant1.example.com/code/")
    .parentLast()
    .build();
```

## Version History

### 1.0 (Current)
- ✅ 20+ ClassSource implementations (Maven, S3, HTTP, FTP, databases, messaging, etc.)
- ✅ Delegation strategies (parent-first, parent-last, custom)
- ✅ Lifecycle hooks for monitoring and resource tracking
- ✅ File system caching
- ✅ Authentication support (Basic, Bearer)
- ✅ Comprehensive test suite (46+ tests)

## Roadmap

### Planned Features
- [ ] JAR file support for remote sources
- [ ] Class signature verification
- [ ] Checksum validation
- [ ] More cache implementations (in-memory, Redis, etc.)
- [ ] Performance metrics dashboard
- [ ] Class version conflict detection
- [ ] Hot reload / class reloading support
