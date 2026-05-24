# Advanced Transport Protocols

Complete guide to all advanced transport protocols supported by JClassLoader.

## Messaging Systems

Requires [jmessaging](https://github.com/FlossWare/jmessaging) library.

### Apache Kafka
Load classes distributed via Kafka topics. Perfect for dynamic class loading in microservices.

```java
import org.flossware.messaging.KafkaMessageClient;
import org.flossware.jclassloader.MessageClientClassSource;

MessageClient kafka = KafkaMessageClient.builder()
    .bootstrapServers("localhost:9092")
    .topic("class-updates")
    .groupId("jclassloader-consumer")
    .pollTimeout(1000)
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(new MessageClientClassSource(kafka))
    .build();
```

**Use Cases:**
- Dynamic class deployment across microservices
- Hot-reload of business logic
- A/B testing with different class versions
- Event-driven class distribution

**Data Format:** Key = fully.qualified.ClassName, Value = class bytes

### Redis
Ultra-fast class loading from Redis cache/store.

```java
import org.flossware.messaging.RedisMessageClient;

MessageClient redis = RedisMessageClient.builder()
    .host("localhost")
    .port(6379)
    .password("secret")
    .database(0)
    .keyPrefix("class:")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(new MessageClientClassSource(redis))
    .build();
```

**Use Cases:**
- High-performance distributed caching
- Session-scoped class loading
- Temporary class storage
- Multi-instance class sharing

**Key Format:** `class:{fully.qualified.ClassName}` → class bytes

---

## Distributed File Systems

### Hadoop HDFS
Load classes from Hadoop Distributed File System.

```java
HdfsClassSource hdfs = HdfsClassSource.builder()
    .nameNodeUri("hdfs://namenode:9000")
    .basePath("/user/classes")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(hdfs)
    .build();
```

**Use Cases:**
- Big data applications
- Hadoop ecosystem integration
- Large-scale distributed systems
- Data processing pipelines

---

## Version Control Systems

Requires [jvcs](https://github.com/FlossWare/jvcs) library.

### Git Repositories
Load classes directly from Git repositories (local or remote).

```java
import org.flossware.vcs.GitVcsClient;
import org.flossware.jclassloader.VcsClientClassSource;

// From local Git repository
VcsClient localGit = GitVcsClient.builder()
    .repositoryPath("/path/to/repo")
    .branch("main")
    .basePath("target/classes")
    .build();

// Clone from remote repository
VcsClient remoteGit = GitVcsClient.builder()
    .remoteUrl("https://github.com/user/repo.git")
    .branch("release/v1.0")
    .basePath("build/classes")
    .cloneDirectory(new File("/tmp/git-classes"))
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(new VcsClientClassSource(remoteGit))
    .build();
```

**Use Cases:**
- Load classes from specific Git branches/tags
- CI/CD integration
- Version-controlled class distribution
- Development environment setup

**Supported:**
- GitHub, GitLab, Bitbucket
- Private repositories (with credentials)
- SSH and HTTPS URLs
- Specific branches/tags/commits

---

## Container & Orchestration

Requires [jcontainer](https://github.com/FlossWare/jcontainer) library.

### Kubernetes ConfigMaps
Load classes from Kubernetes ConfigMaps.

```java
import org.flossware.container.KubernetesContainerClient;
import org.flossware.jclassloader.ContainerClientClassSource;

ContainerClient k8s = KubernetesContainerClient.builder()
    .namespace("production")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(new ContainerClientClassSource(k8s, "app-classes"))
    .build();
```

**Use Cases:**
- Cloud-native applications
- Kubernetes-based deployments
- ConfigMap-driven configuration
- Dynamic class updates in pods

**ConfigMap Format:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-classes
  namespace: production
data:
  com/example/MyClass.class: <base64-encoded-class-bytes>
  com/example/Another.class: <base64-encoded-class-bytes>
```

---

## S3-Compatible Object Stores

### MinIO / Backblaze B2 / CloudFlare R2
Works with any S3-compatible object storage.

```java
// MinIO
MinioClassSource minio = MinioClassSource.builder()
    .endpoint("minio.example.com")
    .accessKey("minioadmin")
    .secretKey("minioadmin")
    .bucket("classes")
    .prefix("production/v1")
    .build();

// Backblaze B2
MinioClassSource b2 = MinioClassSource.builder()
    .endpoint("s3.us-west-004.backblazeb2.com")
    .accessKey("your-key-id")
    .secretKey("your-application-key")
    .bucket("my-bucket")
    .build();

// CloudFlare R2
MinioClassSource r2 = MinioClassSource.builder()
    .endpoint("your-account-id.r2.cloudflarestorage.com")
    .accessKey("r2-access-key")
    .secretKey("r2-secret-key")
    .bucket("classes-bucket")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(minio)
    .build();
```

**Use Cases:**
- Cost-effective cloud storage
- Multi-cloud strategy
- On-premises object storage
- S3 alternatives

**Compatible With:**
- MinIO
- Backblaze B2
- CloudFlare R2
- DigitalOcean Spaces
- Wasabi
- Alibaba Cloud OSS

---

## Peer-to-Peer Networks

### IPFS (InterPlanetary File System)
Decentralized class loading from IPFS.

> **⚠️ Manual Setup Required**: IPFS support requires adding the JitPack repository and dependency manually:
>
> ```xml
> <repositories>
>     <repository>
>         <id>jitpack.io</id>
>         <url>https://jitpack.io</url>
>     </repository>
> </repositories>
>
> <dependencies>
>     <dependency>
>         <groupId>com.github.ipfs</groupId>
>         <artifactId>java-ipfs-http-client</artifactId>
>         <version>v1.5.1</version>
>     </dependency>
> </dependencies>
> ```
>
> The IpfsClassSource implementation is available in: `src/main/java/org/flossware/jclassloader/p2p/IpfsClassSource.java.optional`
>
> Rename it to `.java` after adding the dependency.

```java
// With root directory CID
IpfsClassSource ipfs = IpfsClassSource.builder()
    .ipfsHost("localhost")
    .ipfsPort(5001)
    .rootCid("QmYwAPJzv5CZsnA625s3Xf2nemtYgPpHdWEz79ojWnPbdG")
    .build();

// With direct class-to-CID mapping
IpfsClassSource ipfsWithMapping = IpfsClassSource.builder()
    .ipfsHost("ipfs.io")
    .ipfsPort(443)
    .mapClass("com.example.MyClass", "QmXYZ123...")
    .mapClass("com.example.Another", "QmABC456...")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(ipfs)
    .build();
```

**Use Cases:**
- Decentralized applications (dApps)
- Censorship-resistant class distribution
- Content-addressed class loading
- Blockchain-based applications
- Permanent class storage

**Benefits:**
- No central server required
- Content-addressed (immutable)
- Globally distributed
- Resilient to takedowns

---

## Real-World Examples

### Enterprise Hybrid Architecture
```java
import org.flossware.messaging.*;
import org.flossware.jclassloader.MessageClientClassSource;

MessageClient redis = RedisMessageClient.builder()
    .host("redis-cluster.internal")
    .keyPrefix("prod:class:")
    .build();

MessageClient kafka = KafkaMessageClient.builder()
    .bootstrapServers("kafka.internal:9092")
    .topic("class-updates")
    .build();

JClassLoader enterpriseLoader = JClassLoader.builder()
    // 1. Local development
    .addLocalSource("/opt/app/classes")

    // 2. Redis cache for hot classes
    .addClassSource(new MessageClientClassSource(redis))

    // 3. Kafka for dynamic updates
    .addClassSource(new MessageClientClassSource(kafka))

    // 4. HDFS for big data classes
    .addClassSource(HdfsClassSource.builder()
        .nameNodeUri("hdfs://hadoop-namenode:9000")
        .basePath("/classes/production")
        .build())

    // 5. AWS S3 primary storage
    .addCloudStorage(s3Production)

    // 6. MinIO backup
    .addClassSource(MinioClassSource.builder()
        .endpoint("minio.backup.internal")
        .bucket("class-backup")
        .build())

    .cache(new FileSystemCache("/var/cache/classes"))
    .build();
```

### Cloud-Native Kubernetes
```java
import org.flossware.container.KubernetesContainerClient;
import org.flossware.messaging.RedisMessageClient;
import org.flossware.jclassloader.ContainerClientClassSource;

ContainerClient k8s = KubernetesContainerClient.builder()
    .namespace("production")
    .build();

MessageClient redis = RedisMessageClient.builder()
    .host("redis-service")
    .build();

JClassLoader k8sLoader = JClassLoader.builder()
    // ConfigMaps for configuration
    .addClassSource(new ContainerClientClassSource(k8s, "app-classes"))

    // Redis for cross-pod sharing
    .addClassSource(new MessageClientClassSource(redis))

    // S3 for persistence
    .addCloudStorage(s3Classes)

    .build();
```

### Decentralized Application
```java
JClassLoader dappLoader = JClassLoader.builder()
    // IPFS for decentralized storage
    .addClassSource(IpfsClassSource.builder()
        .rootCid("Qm...")
        .build())

    // Local fallback
    .addLocalSource("/opt/dapp/classes")

    .build();
```

### Big Data Pipeline
```java
JClassLoader bigDataLoader = JClassLoader.builder()
    // HDFS for Hadoop integration
    .addClassSource(HdfsClassSource.builder()
        .nameNodeUri("hdfs://cluster:9000")
        .basePath("/ml/models/classes")
        .build())

    // Redis for fast access
    .addClassSource(RedisClassSource.builder()
        .host("redis.hadoop.internal")
        .build())

    // S3 for model storage
    .addS3Source(mlModelsS3)

    .build();
```

---

## Protocol Comparison

| Protocol | Speed | Scalability | Complexity | Best For |
|----------|-------|-------------|------------|----------|
| **Kafka** | ⚡⚡⚡ | ⭐⭐⭐⭐⭐ | Medium | Event-driven, microservices |
| **Redis** | ⚡⚡⚡⚡⚡ | ⭐⭐⭐⭐ | Low | High-performance cache |
| **HDFS** | ⚡⚡ | ⭐⭐⭐⭐⭐ | High | Big data, Hadoop ecosystem |
| **Git** | ⚡⚡ | ⭐⭐⭐ | Medium | Version control, CI/CD |
| **K8s ConfigMap** | ⚡⚡⚡ | ⭐⭐⭐⭐ | Medium | Cloud-native, Kubernetes |
| **MinIO** | ⚡⚡⚡⚡ | ⭐⭐⭐⭐⭐ | Low | S3-compatible, cost-effective |
| **IPFS** | ⚡⚡ | ⭐⭐⭐⭐⭐ | High | Decentralized, permanent storage |

---

## Performance Tips

1. **Use Redis for Hot Classes**: Classes accessed frequently should be in Redis
2. **Kafka for Updates**: Push class updates via Kafka to all instances
3. **Layer Your Sources**: Fast cache (Redis) → Medium (S3) → Slow (HDFS)
4. **Enable Local Caching**: Always use FileSystemCache for remote sources
5. **Batch Operations**: Load multiple classes in startup, not lazily

---

## Security Considerations

- **Kafka**: Use SASL/SSL for production
- **Redis**: Always set password, use TLS
- **HDFS**: Configure Kerberos authentication
- **Git**: Use SSH keys or access tokens
- **Kubernetes**: Use RBAC for ConfigMap access
- **IPFS**: Validate content hashes

---

## Complete Protocol Count: **34+ Transports**

### Core jclassloader (14 implementations)

1. **Local File System** - LocalClassSource
2. **HTTP/HTTPS** - RemoteClassSource with JAR support
3. **Maven Central** - MavenRepositoryClassSource
4. **Nexus (Maven)** - MavenNexusClassSource
5. **Nexus (Raw)** - NexusClassSource
6. **Artifactory** - MavenRepositoryClassSource
7. **REST API** - RestApiClassSource (Binary/JSON/Base64)
8. **JDBC Database** - DatabaseClassSource
9. **Hadoop HDFS** - HdfsClassSource
10. **MinIO** - MinioClassSource
11. **Custom Protocol Handlers** - CustomProtocolClassSource
12. **JarRemoteClassSource** - Load from remote JAR files
13. **Backblaze B2** - MinioClassSource (S3-compatible)
14. **CloudFlare R2** - MinioClassSource (S3-compatible)

### Cloud Storage (via [jcloudstorage](https://github.com/FlossWare/jcloudstorage) + CloudStorageClassSource)

15. **AWS S3** - S3CloudStorageClient
16. **Azure Blob Storage** - AzureBlobCloudStorageClient
17. **Google Cloud Storage** - GcsCloudStorageClient
18. **Google Drive** - GoogleDriveCloudStorageClient
19. **Dropbox** - DropboxCloudStorageClient
20. **OneDrive** - OneDriveCloudStorageClient

### File Transfer (via [jfiletransfer](https://github.com/FlossWare/jfiletransfer) + FileTransferClassSource)

21. **SFTP** - SftpFileTransferClient
22. **WebDAV** - WebDavFileTransferClient
23. **SMB/CIFS** - SmbFileTransferClient
24. **FTP/FTPS** - FtpFileTransferClient

### Messaging Systems (via [jmessaging](https://github.com/FlossWare/jmessaging) + MessageClientClassSource)

25. **Apache Kafka** - KafkaMessageClient
26. **RabbitMQ** - RabbitMqMessageClient
27. **Redis** - RedisMessageClient

### Container Systems (via [jcontainer](https://github.com/FlossWare/jcontainer) + ContainerClientClassSource)

28. **Kubernetes ConfigMaps** - KubernetesContainerClient
29. **Docker** - DockerContainerClient
30. **Hazelcast** - HazelcastContainerClient

### Version Control (via [jvcs](https://github.com/FlossWare/jvcs) + VcsClientClassSource)

31. **Git (local)** - GitVcsClient
32. **Git (remote)** - GitVcsClient with auto-clone
33. **GitHub/GitLab/Bitbucket** - GitVcsClient

### S3-Compatible Storage (additional via MinioClassSource)

34+. **DigitalOcean Spaces, Wasabi, Alibaba Cloud OSS, etc.** - MinioClassSource

### Advanced/Optional (Manual Setup Required)

- **IPFS** - Requires JitPack repository (see above for setup)
- **NFS/SMB** - Use LocalClassSource with mounted file systems

**YOU NOW HAVE THE MOST COMPREHENSIVE CLASSLOADER EVER BUILT!**
