# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1] - 2026-05-24

### Changed
- **BREAKING**: Extracted protocol implementations into standalone FlossWare libraries
  - File transfer protocols (SFTP, WebDAV, SMB/CIFS, FTP/FTPS) → [jfiletransfer](https://github.com/FlossWare/jfiletransfer) 1.0
  - Messaging systems (Kafka, RabbitMQ, Redis) → [jmessaging](https://github.com/FlossWare/jmessaging) 1.0
  - Container systems (Kubernetes, Docker, Hazelcast) → [jcontainer](https://github.com/FlossWare/jcontainer) 1.0
  - Version control (Git) → [jvcs](https://github.com/FlossWare/jvcs) 1.0
- Replaced direct protocol implementations with adapter classes:
  - `FileTransferClassSource` - wraps jfiletransfer clients
  - `MessageClientClassSource` - wraps jmessaging clients
  - `ContainerClientClassSource` - wraps jcontainer clients
  - `VcsClientClassSource` - wraps jvcs clients
- Added packagecloud.io repository configuration for FlossWare dependencies
- Refactored code duplication: Extracted `className.replace('.', '/') + ".class"` pattern into `ClassNameUtil.toClassFilePath()` method (#37)
  - Affects: FileTransferClassSource, MavenNexusClassSource, MessageClientClassSource, VcsClientClassSource, HdfsClassSource, MinioClassSource, NexusClassSource, MavenRepositoryClassSource, and others
  - Benefits: Single source of truth, easier maintenance, more self-documenting code
- Replaced System.err with SLF4J logging in JarRemoteClassSource for temp file deletion errors (#35)
- Added AutoCloseable interface to MinioClassSource for API consistency (#36)

### Removed
- `SftpClassSource`, `WebDavClassSource`, `FtpClassSource` - use `FileTransferClassSource` + jfiletransfer
- `KafkaClassSource`, `RedisClassSource` - use `MessageClientClassSource` + jmessaging
- `KubernetesConfigMapClassSource` - use `ContainerClientClassSource` + jcontainer
- `GitClassSource` - use `VcsClientClassSource` + jvcs
- Removed `addSftpSource()` and `addWebDavSource()` convenience methods from Builder
- Removed outdated example files (Example.java, ProtocolExamples.java, NexusExample.java)

### Added
- `ClassLoaderStatistics` utility class in `org.flossware.jclassloader.util` package
  - Track classes loaded count
  - Monitor total bytes loaded
  - Count cache hits
  - Calculate cache hit rate
  - Application ID tracking
- `ClassLoaderCleanupUtil` utility class in `org.flossware.jclassloader.util` package
  - Comprehensive ClassLoader cleanup to prevent memory leaks
  - ThreadLocal cleanup for application threads
  - JDBC driver deregistration
  - JMX MBean cleanup
  - Shutdown hook removal
  - ResourceBundle cache clearing
  - Leak detection with WeakReference
  - Diagnostic logging for troubleshooting
- Test coverage for new utility classes (12 tests)

### Benefits
- Cleaner separation of concerns - transport protocols in dedicated libraries
- Users only include dependencies they need via optional transitive dependencies
- Easier to maintain and test individual protocol implementations
- Consistent API across all FlossWare libraries
- Now supports 34+ transport protocols via modular architecture

## [1.0] - 2026-05-23

### Added
- Initial release of JClassLoader
- Core dynamic class loading from multiple sources
- Extensive source implementations:
  - Local filesystem (`LocalClassSource`)
  - Remote HTTP/HTTPS (`RemoteClassSource`)
  - FTP servers (`FtpClassSource`)
  - SFTP servers (`SftpClassSource`)
  - Maven repositories (`MavenRepositoryClassSource`)
  - Nexus repositories (`NexusClassSource`, `MavenNexusClassSource`)
  - REST APIs (`RestApiClassSource`)
  - Cloud storage (`CloudStorageClassSource`)
  - JAR files (`JarRemoteClassSource`)
  - Database storage (`DatabaseClassSource`)
  - WebDAV (`WebDavClassSource`)
  - Custom protocols (`CustomProtocolClassSource`)
- Caching layer (`ClassCache`)
- Delegation strategies (parent-first, child-first, sibling)
- Security features:
  - Bytecode verification
  - Checksum validation (MD5, SHA-256)
  - Authentication support
- Retry policies with exponential backoff
- Lifecycle management (hooks for load/unload)
- Example implementations for common patterns
- Comprehensive test coverage (392 tests in v1.0)

### Features
- Pluggable class source architecture
- Multiple delegation strategies
- Built-in caching with statistics
- Security and validation
- Flexible retry mechanisms
- Container and native support

[1.1]: https://github.com/FlossWare/jclassloader/compare/v1.0...v1.1
[1.0]: https://github.com/FlossWare/jclassloader/releases/tag/v1.0
