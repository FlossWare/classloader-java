/**
 * Core ApplicationClassLoader implementation for loading classes from multiple sources.
 *
 * <p>This package provides the main {@link org.flossware.classloader.ApplicationClassLoader}
 * class and supporting interfaces for loading classes from local and remote sources
 * including HTTP/HTTPS, SFTP, databases, cloud storage, and more.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.flossware.classloader.ApplicationClassLoader} - Main classloader implementation with builder pattern</li>
 *   <li>{@link org.flossware.classloader.ClassSource} - Interface for class loading sources</li>
 *   <li>{@link org.flossware.classloader.AuthConfig} - Authentication configuration for remote sources</li>
 *   <li>{@link org.flossware.classloader.RetryPolicy} - Retry logic for transient failures</li>
 *   <li>{@link org.flossware.classloader.BytecodeVerifier} - Interface for bytecode verification</li>
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
 *   <li>Local filesystem ({@link org.flossware.classloader.LocalClassSource})</li>
 *   <li>HTTP/HTTPS ({@link org.flossware.classloader.RemoteClassSource})</li>
 *   <li>Remote JAR files ({@link org.flossware.classloader.RemoteJarClassSource})</li>
 *   <li>SFTP/SCP ({@link org.flossware.classloader.SftpClassSource})</li>
 *   <li>FTP/FTPS ({@link org.flossware.classloader.FtpClassSource})</li>
 *   <li>WebDAV ({@link org.flossware.classloader.WebDavClassSource})</li>
 *   <li>Databases ({@link org.flossware.classloader.DatabaseClassSource})</li>
 *   <li>Maven repositories ({@link org.flossware.classloader.MavenRepositoryClassSource})</li>
 *   <li>Cloud storage ({@link org.flossware.classloader.CloudStorageClassSource})</li>
 * </ul>
 *
 * @since 1.0
 */
package org.flossware.classloader;
