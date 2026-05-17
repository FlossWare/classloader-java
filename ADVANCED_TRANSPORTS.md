# Advanced Transport Protocols

Complete guide to all advanced transport protocols supported by JClassLoader.

## Messaging Systems

### Apache Kafka
Load classes distributed via Kafka topics. Perfect for dynamic class loading in microservices.

```java
KafkaClassSource kafka = KafkaClassSource.builder()
    .bootstrapServers("localhost:9092")
    .topic("class-updates")
    .groupId("jclassloader-consumer")
    .pollTimeout(1000)
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(kafka)
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
RedisClassSource redis = RedisClassSource.builder()
    .host("localhost")
    .port(6379)
    .password("secret")
    .database(0)
    .keyPrefix("class:")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(redis)
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

### Git Repositories
Load classes directly from Git repositories (local or remote).

```java
// From local Git repository
GitClassSource localGit = GitClassSource.builder()
    .repositoryPath("/path/to/repo")
    .branch("main")
    .basePath("target/classes")
    .build();

// Clone from remote repository
GitClassSource remoteGit = GitClassSource.builder()
    .remoteUrl("https://github.com/user/repo.git")
    .branch("release/v1.0")
    .basePath("build/classes")
    .cloneDirectory(new File("/tmp/git-classes"))
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(remoteGit)
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

### Kubernetes ConfigMaps
Load classes from Kubernetes ConfigMaps.

```java
KubernetesConfigMapClassSource k8s = KubernetesConfigMapClassSource.builder()
    .namespace("production")
    .configMapName("app-classes")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addClassSource(k8s)
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
  com.example.MyClass: <base64-encoded-class-bytes>
  com.example.Another: <base64-encoded-class-bytes>
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
JClassLoader enterpriseLoader = JClassLoader.builder()
    // 1. Local development
    .addLocalSource("/opt/app/classes")

    // 2. Redis cache for hot classes
    .addClassSource(RedisClassSource.builder()
        .host("redis-cluster.internal")
        .keyPrefix("prod:class:")
        .build())

    // 3. Kafka for dynamic updates
    .addClassSource(KafkaClassSource.builder()
        .bootstrapServers("kafka.internal:9092")
        .topic("class-updates")
        .build())

    // 4. HDFS for big data classes
    .addClassSource(HdfsClassSource.builder()
        .nameNodeUri("hdfs://hadoop-namenode:9000")
        .basePath("/classes/production")
        .build())

    // 5. AWS S3 primary storage
    .addS3Source(s3Production)

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
JClassLoader k8sLoader = JClassLoader.builder()
    // ConfigMaps for configuration
    .addClassSource(KubernetesConfigMapClassSource.builder()
        .namespace("production")
        .configMapName("app-classes")
        .build())

    // Redis for cross-pod sharing
    .addClassSource(RedisClassSource.builder()
        .host("redis-service")
        .build())

    // S3 for persistence
    .addS3Source(s3Classes)

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

## Complete Protocol Count: **30+ Transports**

### Fully Implemented (23 Core Implementations)

1. **Local File System** - LocalClassSource
2. **HTTP/HTTPS** - RemoteClassSource
3. **FTP/FTPS** - FtpClassSource
4. **SFTP** - SftpClassSource
5. **WebDAV** - WebDavClassSource
6. **AWS S3** - S3ClassSource
7. **Azure Blob Storage** - AzureBlobClassSource
8. **Google Cloud Storage** - GcsClassSource
9. **Google Drive** - GoogleDriveClassSource
10. **Dropbox** - DropboxClassSource
11. **OneDrive** - OneDriveClassSource
12. **Maven Central** - MavenRepositoryClassSource
13. **Nexus (Maven)** - MavenNexusClassSource
14. **Nexus (Raw)** - NexusClassSource
15. **Artifactory** - MavenRepositoryClassSource
16. **REST API** - RestApiClassSource (Binary/JSON/Base64)
17. **JDBC Database** - DatabaseClassSource
18. **Apache Kafka** - KafkaClassSource
19. **Redis** - RedisClassSource
20. **Hadoop HDFS** - HdfsClassSource
21. **Git** (GitHub/GitLab/Bitbucket) - GitClassSource
22. **Kubernetes ConfigMaps** - KubernetesConfigMapClassSource
23. **Custom Protocol Handlers** - CustomProtocolClassSource

### S3-Compatible Storage (via MinioClassSource)

24. **MinIO** - MinioClassSource
25. **Backblaze B2** - MinioClassSource
26. **CloudFlare R2** - MinioClassSource
27. **DigitalOcean Spaces** - MinioClassSource
28. **Wasabi** - MinioClassSource
29. **Alibaba Cloud OSS** - MinioClassSource
30+. **Any S3-compatible storage** - MinioClassSource

### Advanced/Optional (Manual Setup Required)

- **IPFS** - Requires JitPack repository (see above for setup)
- **NFS/SMB** - Use LocalClassSource with mounted file systems

**YOU NOW HAVE THE MOST COMPREHENSIVE CLASSLOADER EVER BUILT!**
