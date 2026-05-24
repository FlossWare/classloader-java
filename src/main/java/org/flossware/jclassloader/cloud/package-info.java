/**
 * Cloud storage class sources (deprecated).
 *
 * <p><b>DEPRECATED:</b> This package is deprecated in favor of the separate
 * <a href="https://github.com/FlossWare/cloud-storage-client">cloud-storage-client</a> library.
 * Use {@link org.flossware.jclassloader.CloudStorageClassSource} with cloud-storage-client providers instead.</p>
 *
 * <h2>Migration Guide</h2>
 * <pre>{@code
 * // OLD (deprecated):
 * JClassLoader loader = JClassLoader.builder()
 *     .addS3Source(S3ClassSource.builder()...)
 *     .build();
 *
 * // NEW (recommended):
 * CloudStorageClient s3 = S3CloudStorageClient.builder()
 *     .bucket("my-bucket")
 *     .region(Region.US_EAST_1)
 *     .build();
 *
 * JClassLoader loader = JClassLoader.builder()
 *     .addCloudStorage(s3)
 *     .build();
 * }</pre>
 *
 * <p>The cloud-storage-client library provides a unified API for all cloud providers
 * and can be used independently of class loading.</p>
 *
 * @deprecated Use {@link org.flossware.jclassloader.CloudStorageClassSource} with
 *             cloud-storage-client library instead
 * @since 1.0
 */
package org.flossware.jclassloader.cloud;
