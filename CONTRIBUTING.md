# Contributing to ApplicationClassLoader

Thank you for your interest in contributing to ApplicationClassLoader!

## How to Contribute

### Reporting Bugs

1. **Check existing issues** - Your bug may already be reported
2. **Use the bug report template** - Provide all requested information
3. **Include details**:
   - classloader-java version
   - Java version (output of `java -version`)
   - Operating system
   - Full stack trace
   - Minimal reproducible example

### Suggesting Enhancements

1. **Open a GitHub Issue** with the enhancement label
2. **Describe the use case** - Why is this feature needed?
3. **Propose a solution** - How should it work?
4. **Consider alternatives** - What other approaches could solve this?

### Pull Requests

1. **Fork the repository**
   ```bash
   git clone https://github.com/YOUR-USERNAME/classloader-java.git
   cd classloader-java
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/my-feature
   ```

3. **Make your changes**
   - Write clean, readable code
   - Follow existing code style
   - Add JavaDoc for public APIs
   - No wildcard imports (`import java.util.*` - use specific imports)

4. **Add tests**
   - All new features require unit tests
   - Bug fixes should include regression tests
   - Aim for high test coverage (target: 80%+)
   - Run tests: `mvn clean test`

5. **Update documentation**
   - Update README.md if adding new features
   - Update JavaDoc for API changes
   - Add examples if appropriate

6. **Commit your changes**
   ```bash
   git add .
   git commit -m "Brief description of changes
   
   Detailed explanation of what changed and why.
   
   Fixes #123"
   ```

7. **Push and create Pull Request**
   ```bash
   git push origin feature/my-feature
   ```
   Then open a PR on GitHub

### Code Style Guidelines

#### General
- **No wildcard imports** - Use specific imports (`import java.util.List;` not `import java.util.*;`)
- **Prefer specific exceptions** - Don't throw or catch generic `Exception`
- **Implement AutoCloseable** - For classes that hold resources (connections, files, etc.)
- **Immutable value objects** - Make value objects `final` with `equals/hashCode/toString`

#### JavaDoc
- **All public classes** must have class-level JavaDoc
- **All public methods** must have JavaDoc with:
  - Description of what the method does
  - `@param` for each parameter
  - `@return` for return values
  - `@throws` for checked exceptions
- **Include examples** in JavaDoc when helpful

Example:
```java
/**
 * Loads class data from a remote HTTP/HTTPS source.
 * Supports optional authentication via Basic or Bearer token.
 *
 * @param className The fully qualified class name (e.g., "com.example.MyClass")
 * @return The class bytecode as a byte array
 * @throws IOException if the class cannot be loaded or network error occurs
 */
@Override
public byte[] loadClassData(String className) throws IOException {
    // Implementation
}
```

#### Testing
- **Use JUnit 5** - `@Test`, not JUnit 4
- **Mock external dependencies** - Use Mockito for mocking HTTP clients, databases, etc.
- **Test edge cases** - Null inputs, empty strings, invalid data
- **No integration tests in unit tests** - Mock external services (S3, databases, Kafka)

Example test:
```java
@Test
void testLoadClassDataThrowsIOExceptionOnNetworkError() throws IOException {
    HttpURLConnection mockConnection = mock(HttpURLConnection.class);
    when(mockConnection.getResponseCode()).thenReturn(500);
    
    RemoteClassSource source = new RemoteClassSource("https://example.com");
    
    assertThrows(IOException.class, () -> source.loadClassData("com.example.MyClass"));
}
```

### Testing Your Changes

```bash
# Run all tests
mvn clean test

# Run tests with coverage
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html

# Run checkstyle (if configured)
mvn checkstyle:check

# Full verification
mvn clean verify
```

### Building the Project

```bash
# Build without tests
mvn clean install -DskipTests

# Build with tests
mvn clean install

# Generate JavaDoc
mvn javadoc:javadoc

# View JavaDoc
open target/site/apidocs/index.html
```

## Code Review Process

1. **Automated checks** run on all PRs:
   - Tests must pass (493+ tests)
   - Code must compile
   - No wildcard imports
   
2. **Maintainer review**:
   - Code quality and style
   - Test coverage
   - Documentation
   - Breaking changes

3. **Feedback and iteration**:
   - Address review comments
   - Update PR as needed
   - Maintainer will merge when ready

## Contribution Agreement

By contributing to ApplicationClassLoader, you agree that:
- Your contributions will be licensed under the **GNU General Public License v3.0**
- You have the right to contribute the code (you own it or have permission)
- You understand that contributions may be modified or rejected

## Getting Help

- **GitHub Issues** - Ask questions with the "question" label
- **GitHub Discussions** - For general discussions and design questions
- **Documentation** - Check README.md, JavaDoc, and code examples

## Code of Conduct

This project adheres to the Contributor Covenant Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainer.

## Recognition

Contributors are recognized in:
- Git commit history
- GitHub contributors page
- Release notes (for significant contributions)

## Release Process

**Note:** Only project maintainers can create releases.

### Creating a Release

Releases are created through the GitHub Actions workflow:

1. **Navigate to Actions** → "Release" workflow
2. **Click "Run workflow"**
3. **Enter version** (e.g., `2.1`, `3.0`)
   - Must follow `X.Y` format (semantic versioning: major.minor)
   - No `-SNAPSHOT` suffix for releases

### What Happens During Release

The workflow automatically:

1. ✅ Validates version format
2. ✅ Updates `pom.xml` to release version
3. ✅ Runs full test suite
4. ✅ Builds and signs artifacts (requires GPG key)
5. ✅ Deploys to Maven Central via OSSRH
6. ✅ Creates Git tag (`vX.Y`)
7. ✅ Generates changelog from commit history
8. ✅ Creates GitHub Release with installation instructions
9. ✅ Bumps `pom.xml` to next development version (`X.(Y+1)-SNAPSHOT`)

### Prerequisites for Releases

Maintainers must configure these GitHub secrets:

- `OSSRH_USERNAME` - Sonatype OSSRH username
- `OSSRH_TOKEN` - Sonatype OSSRH token
- `GPG_PRIVATE_KEY` - GPG private key for signing artifacts
- `GPG_PASSPHRASE` - GPG key passphrase

### Maven Central Deployment

After release:
- Artifacts appear on Maven Central within 30 minutes to 2 hours
- Users can then add the dependency:

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>classloader-java</artifactId>
    <version>X.Y</version>
</dependency>
```

### Release Checklist

Before creating a release:

- [ ] All tests passing on `main` branch
- [ ] CHANGELOG.md updated with release notes
- [ ] Version number decided (follows semantic versioning)
- [ ] Breaking changes documented
- [ ] Security issues addressed
- [ ] Quality gate passing (46% instruction coverage)

### Hotfix Releases

For critical bug fixes:

1. Create hotfix branch from release tag
2. Apply fix and increment minor version
3. Follow normal release process
4. Merge back to main

### Version Numbering

This project follows **Semantic Versioning 2.0.0**:

- **Major version (X.0)**: Breaking changes, incompatible API changes
- **Minor version (X.Y)**: New features, backwards-compatible enhancements
- **Patch version**: Not used (we only release X.Y versions)

Examples:
- `2.0` → `3.0`: Breaking change (extracted protocols to separate libraries)
- `2.0` → `2.1`: New features, backwards-compatible
- `2.1` → `3.0`: Another breaking change

Thank you for contributing to ApplicationClassLoader!
