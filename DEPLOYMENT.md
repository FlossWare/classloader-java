# Deployment Guide

This guide covers how to deploy classloader-java to Maven Central.

## Prerequisites

### 1. Sonatype OSSRH Account

1. Create a JIRA account at https://issues.sonatype.org
2. Create a "New Project" ticket requesting access to `org.flossware` groupId
3. Wait for approval (usually 1-2 business days)

### 2. GPG Key Setup

```bash
# Generate GPG key (if you don't have one)
gpg --gen-key

# List your keys
gpg --list-keys

# Publish your public key to key servers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 3. Maven Settings

Add to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_PASSWORD</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

**Security Note:** Consider using encrypted passwords or environment variables instead of plain text.

## Deployment Steps

### 1. Prepare Release

```bash
# Ensure you're on main branch and up to date
git checkout main
git pull

# Ensure all tests pass
mvn clean verify

# Check version in pom.xml is correct (should be X.Y, not X.Y-SNAPSHOT)
```

### 2. Deploy to Maven Central

```bash
# Deploy using the release profile
mvn clean deploy -P release

# This will:
# 1. Build the project
# 2. Run all tests
# 3. Generate source JAR
# 4. Generate javadoc JAR
# 5. Sign all artifacts with GPG
# 6. Upload to OSSRH staging repository
# 7. Automatically release to Maven Central (if autoReleaseAfterClose=true)
```

### 3. Verify Deployment

After deployment, artifacts will appear in Maven Central within:
- Staging: Immediately
- Search: 2-4 hours
- Full sync: Up to 24 hours

Check at: https://search.maven.org/artifact/org.flossware/classloader-java

### 4. Tag Release

```bash
# Create and push git tag
git tag -a v2.0 -m "Release version 2.0"
git push origin v2.0

# Create GitHub release
gh release create v2.0 --title "v2.0" --notes "Release notes here"
```

## Version Management

### Release Versions

- Format: `X.Y` (e.g., `2.0`, `2.1`)
- No `-SNAPSHOT` suffix
- Use for stable, production-ready releases

### Snapshot Versions

- Format: `X.Y-SNAPSHOT` (e.g., `2.1-SNAPSHOT`)
- For development/testing
- Deployed to snapshot repository
- Can be overwritten

### Version Bumping

```bash
# After releasing 2.0, bump to next snapshot
mvn versions:set -DnewVersion=2.1-SNAPSHOT
git commit -am "Bump version to 2.1-SNAPSHOT"
git push
```

## Troubleshooting

### GPG Signing Fails

```bash
# Verify GPG can sign
echo "test" | gpg --clearsign

# If prompted for passphrase, add to settings.xml or use:
export GPG_TTY=$(tty)
```

### Upload Fails

- Check credentials in `~/.m2/settings.xml`
- Verify OSSRH JIRA ticket is approved for `org.flossware`
- Ensure version is not `-SNAPSHOT` for releases

### Artifacts Not Appearing

- Check OSSRH staging repository: https://s01.oss.sonatype.org/
- Verify signing succeeded (all `.asc` files present)
- Ensure all required files are present:
  - `classloader-java-X.Y.jar`
  - `classloader-java-X.Y-sources.jar`
  - `classloader-java-X.Y-javadoc.jar`
  - `classloader-java-X.Y.pom`
  - `.asc` signature files for each

## Manual Release (if autoReleaseAfterClose=false)

```bash
# Deploy to staging
mvn clean deploy -P release

# Login to OSSRH
# Go to: https://s01.oss.sonatype.org/#stagingRepositories
# Find your repository (orgflossware-XXXX)
# Click "Close"
# Wait for validation
# Click "Release"
```

## Alternative: packagecloud (Legacy)

The project was previously deployed to packagecloud:

```bash
# Install packagecloud CLI
gem install package_cloud

# Deploy to packagecloud
mvn clean package
package_cloud push flossware/java target/classloader-java-*.jar
```

## Security Best Practices

- ✅ Never commit credentials to version control
- ✅ Use encrypted passwords in settings.xml
- ✅ Rotate GPG keys periodically
- ✅ Keep private keys secure
- ✅ Use environment variables for CI/CD
- ✅ Enable 2FA on Sonatype account

## CI/CD Integration

For automated releases via GitHub Actions, see `.github/workflows/` examples:

- Store secrets in GitHub repository secrets
- Use GPG key export for CI environments
- Automate version bumping and tagging

## References

- [Maven Central OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)
- [Nexus Staging Plugin](https://github.com/sonatype/nexus-maven-plugins)
