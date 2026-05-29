/**
 * Core ApplicationClassLoader implementation for loading classes from multiple sources.
 *
 * <p>This package provides the main {@link org.flossware.jclassloader.ApplicationClassLoader}
 * class and supporting interfaces for loading classes from local and remote sources
 * including HTTP/HTTPS, SFTP, databases, cloud storage, and more.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.flossware.jclassloader.ApplicationClassLoader} - Main classloader implementation with builder pattern</li>
 *   <li>{@link org.flossware.jclassloader.ClassSource} - Interface for class loading sources</li>
 *   <li>{@link org.flossware.jclassloader.AuthConfig} - Authentication configuration for remote sources</li>
 *   <li>{@link org.flossware.jclassloader.RetryPolicy} - Retry logic for transient failures</li>
 *   <li>{@link org.flossware.jclassloader.BytecodeVerifier} - Interface for bytecode verification</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Load from multiple sources
 * ApplicationClassLoader loader = ApplicationClassLoader.builder()
 *     .addLocalSource("/path/to/classes")
 *     .addRemoteSource("https://example.com/classes/")
 *     .addRemoteJar("https://example.com/libs/my-lib.jar")
 *     .bytecodeVerifier(new ChecksumValidator(checksums))
 *     .build();
 *
 * // Load a class
 * Class<?> myClass = loader.loadClass("com.example.MyClass");
 * Object instance = myClass.getDeclaredConstructor().newInstance();
 *
 * // Always close when done
 * loader.close();
 * }</pre>
 *
 * <h2>Supported Class Sources</h2>
 * <ul>
 *   <li>Local filesystem ({@link org.flossware.jclassloader.LocalClassSource})</li>
 *   <li>HTTP/HTTPS ({@link org.flossware.jclassloader.RemoteClassSource})</li>
 *   <li>Remote JAR files ({@link org.flossware.jclassloader.JarRemoteClassSource})</li>
 *   <li>SFTP/SCP ({@link org.flossware.jclassloader.SftpClassSource})</li>
 *   <li>FTP/FTPS ({@link org.flossware.jclassloader.FtpClassSource})</li>
 *   <li>WebDAV ({@link org.flossware.jclassloader.WebDavClassSource})</li>
 *   <li>Databases ({@link org.flossware.jclassloader.DatabaseClassSource})</li>
 *   <li>Maven repositories ({@link org.flossware.jclassloader.MavenRepositoryClassSource})</li>
 *   <li>Cloud storage ({@link org.flossware.jclassloader.CloudStorageClassSource})</li>
 * </ul>
 *
 * @since 1.0
 */
package org.flossware.classloader;
