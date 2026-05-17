# JClassLoader

A flexible Java ClassLoader that can load classes from both local and remote locations (HTTP/HTTPS/FTP/FTPS) with built-in caching support and authentication.

## Features

- **Local Class Loading**: Load classes from local file system directories
- **Remote Class Loading**: Load classes from HTTP/HTTPS URLs
- **FTP/FTPS Support**: Load classes from FTP and FTPS servers
- **Nexus Repository Support**: Load classes from Sonatype Nexus repositories (both raw and Maven repositories)
- **Maven Artifact Resolution**: Automatically extract classes from Maven JARs hosted in Nexus
- **Caching**: Optional file-system based caching to avoid repeated downloads
- **Authentication**: Support for HTTP Basic and Bearer token authentication
- **Builder Pattern**: Fluent API for easy configuration
- **Extensible**: Add custom class sources by implementing the `ClassSource` interface

## Requirements

- Java 11 or higher
- Maven 3.6 or higher (for building)

## Installation

### Maven

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jclassloader</artifactId>
    <version>1.0.0-SNAPSHOT</version>
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

## Architecture

### Core Components

- **`JClassLoader`**: Main classloader implementation extending `java.lang.ClassLoader`
- **`ClassSource`**: Interface for class loading sources
- **`LocalClassSource`**: Loads classes from local file system
- **`RemoteClassSource`**: Loads classes from HTTP/HTTPS URLs
- **`FtpClassSource`**: Loads classes from FTP/FTPS servers
- **`NexusClassSource`**: Loads classes from Nexus raw or Maven repositories
- **`MavenNexusClassSource`**: Specialized source for loading classes from Maven artifacts in Nexus
- **`MavenArtifact`**: Represents Maven coordinates and handles path resolution
- **`ClassCache`**: Interface for caching implementations
- **`FileSystemCache`**: File-based cache implementation
- **`AuthConfig`**: Configuration for authentication

### How It Works

1. When `loadClass()` is called, JClassLoader first delegates to its parent classloader
2. If the parent can't find the class, it checks the cache (if enabled)
3. If not in cache, it iterates through configured class sources in order
4. The first source that can load the class provides the bytecode
5. The class is defined and optionally cached for future use

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

[Add your license here]

## Authors

- Scot P. Floess

## Roadmap

- [ ] JAR file support for remote sources
- [ ] Class signature verification
- [ ] Checksum validation
- [ ] More cache implementations (in-memory, Redis, etc.)
- [ ] Performance metrics and monitoring
- [ ] Class version conflict detection
