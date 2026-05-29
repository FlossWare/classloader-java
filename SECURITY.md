# Security Considerations for Remote Bytecode Loading

## ⚠️ Security Warning

**Loading classes from untrusted sources is extremely dangerous and can lead to:**
- Remote code execution (RCE)
- Data exfiltration
- Privilege escalation
- Complete system compromise

**Only load classes from sources you completely trust and control.**

## Threat Model

### Attack Vectors

1. **Malicious Bytecode Injection**
   - Attacker replaces legitimate class with malicious one
   - Malicious code executes with full JVM privileges
   - Can access file system, network, system resources

2. **Man-in-the-Middle (MITM) Attacks**
   - Attacker intercepts HTTP traffic and injects malicious bytecode
   - Applies to: RemoteClassSource, RestApiClassSource, HTTP-based sources

3. **Supply Chain Attacks**
   - Compromised Maven repository serves malicious JARs
   - Applies to: MavenNexusClassSource, MavenRepositoryClassSource

4. **Credential Theft**
   - Hardcoded credentials in code
   - Credentials logged or leaked
   - Applies to: All authenticated sources

5. **Deserialization Attacks**
   - Malicious serialized objects in class static initializers
   - Can trigger on class load without instantiation

## Security Controls

### 1. Transport Security

**✅ ALWAYS use HTTPS/TLS for remote sources**

\`\`\`java
// ❌ INSECURE - HTTP is vulnerable to MITM
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("http://example.com/classes/")  // NO!
    .build();

// ✅ SECURE - HTTPS encrypts traffic
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("https://example.com/classes/")  // YES!
    .build();
\`\`\`

**Certificate Validation:**
- Never disable certificate validation in production
- Pin certificates for critical sources
- Use internal CA for private deployments

### 2. Bytecode Verification

**Use ChecksumValidator to verify class integrity:**

\`\`\`java
// 1. Generate checksums for trusted classes
Map<String, String> trustedChecksums = Map.of(
    "com.example.TrustedClass",
    "a1b2c3d4..." // SHA-256 checksum
);

// 2. Create validator
BytecodeVerifier verifier = new ChecksumValidator(trustedChecksums);

// 3. Configure loader with verification
ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("https://example.com/classes/")
    .bytecodeVerifier(verifier)  // Rejects any class that doesn't match
    .build();

// 4. Load will fail if checksum doesn't match
try {
    Class<?> clazz = loader.loadClass("com.example.TrustedClass");
} catch (SecurityException e) {
    // Checksum mismatch - potential attack!
    log.error("SECURITY: Class failed verification", e);
}
\`\`\`

**Generating Checksums:**

\`\`\`bash
# For .class files
sha256sum MyClass.class

# For classes in JARs
unzip -p mylib.jar com/example/MyClass.class | sha256sum
\`\`\`

### 3. Source Authentication

**Always authenticate to private repositories:**

\`\`\`java
// ✅ Use proper authentication
AuthConfig auth = AuthConfig.basic(
    System.getenv("NEXUS_USER"),      // From environment
    System.getenv("NEXUS_PASSWORD")   // NOT hardcoded!
);

ApplicationClassLoader loader = ApplicationClassLoader.builder()
    .addRemoteSource("https://private-repo.example.com/", auth)
    .build();
\`\`\`

**Credential Management Best Practices:**
- ❌ Never hardcode credentials in source code
- ❌ Never log credentials
- ❌ Never commit credentials to version control
- ✅ Use environment variables or secret management systems
- ✅ Rotate credentials regularly
- ✅ Use least-privilege accounts
- ✅ Enable audit logging on repositories

### 4. Size Limits

**All ClassSource implementations now enforce size limits:**

The library automatically enforces size limits to prevent DoS attacks:
- Class files: 10MB maximum (configurable)
- JAR files: 100MB maximum (configurable)

This prevents out-of-memory (OOM) attacks, disk exhaustion, and denial of service.

## Security by Source Type

### RemoteClassSource / RestApiClassSource
**Risk Level: HIGH**
- Direct HTTP(S) access to arbitrary URLs
- Vulnerable to MITM if not using HTTPS
- **Mitigation:**
  - Always use HTTPS
  - Use ChecksumValidator
  - Whitelist allowed URLs
  - Configure timeouts (prevent DoS)

### MavenNexusClassSource / MavenRepositoryClassSource
**Risk Level: MEDIUM-HIGH**
- Trusted repository can be compromised
- Dependencies can have vulnerabilities
- **Mitigation:**
  - Use private, secured Nexus instance
  - Enable repository signing
  - Use ChecksumValidator
  - Audit all dependencies
  - Keep dependencies updated

### MinioClassSource / Cloud Storage
**Risk Level: HIGH**
- S3/MinIO buckets can be misconfigured (public access)
- Credentials can be stolen
- **Mitigation:**
  - Never use public buckets
  - Use IAM roles (not access keys) when possible
  - Enable bucket versioning (detect tampering)
  - Enable access logging
  - Use ChecksumValidator

### DatabaseClassSource
**Risk Level: CRITICAL**
- Database compromise = code execution
- SQL injection can inject malicious bytecode
- **Mitigation:**
  - Use parameterized queries
  - Restrict database permissions
  - Enable audit logging
  - Use ChecksumValidator
  - Isolate class storage database

### Messaging Sources (Kafka, RabbitMQ, Redis)
**Risk Level: CRITICAL**
- Message tampering can inject malicious code
- Difficult to audit/trace
- **Mitigation:**
  - Use TLS for all messaging connections
  - Enable message signing/encryption
  - Use ChecksumValidator (mandatory!)
  - Implement message authentication
  - Audit all class updates

### LocalClassSource
**Risk Level: LOW**
- Local file system access
- **Mitigation:**
  - Restrict file permissions (read-only if possible)
  - Monitor for unauthorized file changes
  - Use file integrity monitoring (FIM)

## Security Checklist

Before deploying to production:

- [ ] All remote sources use HTTPS (never HTTP)
- [ ] ChecksumValidator configured for all untrusted sources
- [ ] Credentials stored in environment variables or secret manager
- [ ] No credentials hardcoded or committed to version control
- [ ] Network firewall rules restrict outbound connections
- [ ] Size limits configured appropriately
- [ ] Timeouts configured (prevent DoS)
- [ ] Audit logging enabled
- [ ] Incident response plan in place
- [ ] Regular security audits scheduled
- [ ] Dependency vulnerabilities monitored

## Reporting Security Issues

**Do NOT open public GitHub issues for security vulnerabilities.**

Email: scot.floess@gmail.com

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

We aim to respond within 48 hours.

## References

- [OWASP Java Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Java_Security_Cheat_Sheet.html)
- [CWE-494: Download of Code Without Integrity Check](https://cwe.mitre.org/data/definitions/494.html)
- [CWE-829: Inclusion of Functionality from Untrusted Control Sphere](https://cwe.mitre.org/data/definitions/829.html)

## License

This security guide is part of the classloader-java project and is licensed under the GNU General Public License v3.0.
