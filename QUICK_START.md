# JClassLoader - Quick Start Guide

## Installation

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jclassloader</artifactId>
    <version>1.0</version>
</dependency>
```

**Note**: All protocol dependencies are `optional`. Only include the ones you need:

```xml
<!-- For Cloud Storage (S3, Azure, GCS, Google Drive, Dropbox, OneDrive) -->
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jcloudstorage</artifactId>
    <version>1.0</version>
</dependency>
<!-- Plus provider SDK (e.g., AWS SDK, Azure SDK) -->

<!-- For SFTP -->
<dependency>
    <groupId>com.github.mwiede</groupId>
    <artifactId>jsch</artifactId>
    <version>0.2.21</version>
</dependency>
<!-- etc. -->
```

## 30-Second Examples

### Load from Maven Central
```java
JClassLoader loader = JClassLoader.builder()
    .addMavenCentral("org.apache.commons:commons-lang3:3.12.0")
    .build();

Class<?> stringUtils = loader.loadClass("org.apache.commons.lang3.StringUtils");
```

### Load from AWS S3
```java
import org.flossware.cloud.storage.S3CloudStorageClient;

CloudStorageClient s3 = S3CloudStorageClient.builder()
    .region("us-east-1")
    .bucket("my-classes-bucket")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addCloudStorage(s3)
    .build();
```

### Load from Google Drive
```java
import org.flossware.cloud.storage.GoogleDriveCloudStorageClient;

CloudStorageClient drive = GoogleDriveCloudStorageClient.builder()
    .credentialsPath("credentials.json")
    .folderId("folder-id-here")
    .build();

JClassLoader loader = JClassLoader.builder()
    .addCloudStorage(drive)
    .build();
```

### Load from SFTP
```java
JClassLoader loader = JClassLoader.builder()
    .addSftpSource("sftp.example.com", "username", "password", "/classes")
    .build();
```

### Load from Database
```java
DataSource ds = ...; // Your JDBC DataSource

JClassLoader loader = JClassLoader.builder()
    .addDatabaseSource(ds, "class_storage", "class_name", "class_bytes")
    .build();
```

### Multi-Source with Fallback & Caching
```java
import org.flossware.jclassloader.cache.FileSystemCache;

JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/opt/app/classes")              // Check local first
    .addCloudStorage(s3Production)                   // Then S3
    .addMavenCentral("commons:lang:3.12.0")          // Then Maven Central
    .cache(new FileSystemCache("/tmp/cache"))         // With caching
    .build();
```

### Parent-Last Isolation (NEW in 1.0)
```java
// For plugin systems, containers, isolation
JClassLoader pluginLoader = JClassLoader.builder()
    .addLocalSource("/plugins/my-plugin")
    .parentLast("com.myapp.api.")  // Only API from parent
    .addLoggingListener()           // Monitor class loading
    .build();
```

### Resource Tracking (NEW in 1.0)
```java
import org.flossware.jclassloader.lifecycle.ResourceTrackingListener;

ResourceTrackingListener tracker = new ResourceTrackingListener();

JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/path/to/classes")
    .addListener(tracker)
    .build();

// Later: get statistics
System.out.println("Classes loaded: " + tracker.getTotalClassesLoaded());
System.out.println("Cache hit rate: " + 
    (tracker.getCacheHits() * 100.0 / tracker.getTotalClassesLoaded()) + "%");

// Cleanup when done
tracker.closeAllResources();
```

## Common Use Cases

### 1. Cloud-First with Local Fallback
```java
JClassLoader loader = JClassLoader.builder()
    .addCloudStorage(productionS3)
    .addLocalSource("/opt/backup/classes")
    .cache(new FileSystemCache("/var/cache/classes"))
    .build();
```

### 2. Multi-Cloud Redundancy
```java
JClassLoader loader = JClassLoader.builder()
    .addCloudStorage(awsS3Primary)       // Primary: AWS
    .addCloudStorage(azureBackup)        // Backup: Azure
    .addCloudStorage(gcsShared)          // Shared: GCS
    .build();
```

### 3. Team Collaboration
```java
JClassLoader loader = JClassLoader.builder()
    .addLocalSource("/home/dev/overrides")
    .addCloudStorage(teamDrive)
    .addMavenRepository(internalMaven)
    .build();
```

### 4. Secure Enterprise
```java
JClassLoader loader = JClassLoader.builder()
    .addSftpSource("secure-server.com", "deploy", privateKey, "/classes")
    .addNexusMavenSource(nexusWithAuth)
    .addDatabaseSource(encryptedDB, "classes", "name", "bytes")
    .build();
```

## Protocol Comparison

| Protocol | Best For | Auth | Speed |
|----------|----------|------|-------|
| **Local** | Development, fastest access | N/A | ⚡⚡⚡⚡⚡ |
| **AWS S3** | Production, scalable | IAM/Keys | ⚡⚡⚡⚡ |
| **Azure Blob** | Microsoft cloud | Keys/SAS | ⚡⚡⚡⚡ |
| **GCS** | Google cloud | Service Account | ⚡⚡⚡⚡ |
| **Maven Central** | Open source libs | Public | ⚡⚡⚡ |
| **Google Drive** | Team sharing | OAuth | ⚡⚡⚡ |
| **Dropbox** | Team sharing | Token | ⚡⚡⚡ |
| **SFTP** | Secure transfer | SSH Key/Password | ⚡⚡ |
| **Database** | Transaction consistency | JDBC | ⚡⚡ |
| **HTTP/HTTPS** | Simple remote | Basic/Bearer | ⚡⚡ |
| **REST API** | Custom systems | Configurable | ⚡⚡ |

## Best Practices

### 1. Always Use Caching in Production
```java
FileSystemCache cache = new FileSystemCache("/var/cache/jclassloader");

JClassLoader loader = JClassLoader.builder()
    .addCloudStorage(s3)
    .cache(cache)
    .useCache(true)  // Important!
    .build();
```

### 2. Order Sources by Speed
```java
JClassLoader loader = JClassLoader.builder()
    .addLocalSource(...)       // Fastest
    .addCloudStorage(...)      // Fast
    .addSftpSource(...)        // Slower
    .addRestApiSource(...)     // Slowest
    .build();
```

### 3. Use Authentication for Security
```java
import org.flossware.cloud.storage.S3CloudStorageClient;

// Good - Authenticated
CloudStorageClient s3 = S3CloudStorageClient.builder()
    .region("us-east-1")
    .bucket("private-bucket")
    .credentials(accessKey, secretKey)
    .build();

// Better - IAM Roles (no hardcoded credentials)
CloudStorageClient s3Auto = S3CloudStorageClient.builder()
    .region("us-east-1")
    .bucket("private-bucket")
    // Uses IAM role automatically
    .build();
```

### 4. Handle Failures Gracefully
```java
try {
    Class<?> myClass = loader.loadClass("com.example.MyClass");
    Object instance = myClass.getDeclaredConstructor().newInstance();
} catch (ClassNotFoundException e) {
    // Class not found in any source
    logger.error("Class not available: {}", e.getMessage());
} catch (Exception e) {
    // Other instantiation errors
    logger.error("Failed to instantiate: {}", e.getMessage());
}
```

## Performance Tips

1. **Cache Everything**: Enable caching for remote sources
2. **Order Matters**: Put fastest sources first
3. **Minimize Sources**: Only add sources you actually need
4. **Connection Pooling**: Reuse connections where possible
5. **Prefetch**: Load common classes during startup

## Security Checklist

- ✅ Use HTTPS/FTPS/SFTP for network protocols
- ✅ Store credentials in secure vaults (AWS Secrets Manager, etc.)
- ✅ Use IAM roles instead of hardcoded keys when possible
- ✅ Validate class signatures before loading (if needed)
- ✅ Implement class whitelisting/blacklisting
- ✅ Use VPN for accessing internal resources
- ✅ Rotate credentials regularly
- ✅ Monitor class loading for anomalies

## Troubleshooting

### Classes Not Found
```java
// Enable debug logging to see which sources are checked
loader.getClassSources().forEach(source ->
    System.out.println("Source: " + source.getDescription())
);
```

### Slow Performance
1. Enable caching
2. Reorder sources (fastest first)
3. Check network latency
4. Use connection pooling

### Authentication Failures
1. Verify credentials are correct
2. Check IAM permissions (for cloud)
3. Ensure OAuth tokens haven't expired
4. Test credentials outside JClassLoader first

## Next Steps

- Read [PROTOCOLS.md](PROTOCOLS.md) for detailed protocol documentation
- Check [examples/](src/main/java/org/flossware/jclassloader/example/) for more code samples
- Review [README.md](README.md) for architecture details

## Support

- Issues: https://github.com/FlossWare/jclassloader/issues
- Email: support@flossware.org
