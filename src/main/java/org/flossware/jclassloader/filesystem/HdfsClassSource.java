package org.flossware.jclassloader.filesystem;

import org.apache.hadoop.conf.Configuration;

import static org.flossware.jclassloader.util.ClassLoaderConstants.DEFAULT_BUFFER_SIZE;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.flossware.jclassloader.ClassSource;
import org.flossware.jclassloader.util.ClassNameUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * ClassSource implementation for loading classes from Hadoop HDFS.
 * Supports distributed file system access for class loading in Hadoop clusters.
 * Requires the Hadoop client dependency.
 */
public class HdfsClassSource implements ClassSource, AutoCloseable {
    private final FileSystem hdfs;
    private final String basePath;

    private HdfsClassSource(FileSystem hdfs, String basePath) {
        this.hdfs = Objects.requireNonNull(hdfs, "hdfs cannot be null");
        this.basePath = basePath != null ? basePath : "/";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        Path classPath = getClassPath(className);

        try (InputStream in = hdfs.open(classPath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    @Override
    public boolean canLoad(String className) {
        try {
            Path classPath = getClassPath(className);
            return hdfs.exists(classPath) && hdfs.isFile(classPath);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "HdfsClassSource[basePath=" + basePath + "]";
    }

    private Path getClassPath(String className) {
        String classFile = ClassNameUtil.toClassFilePath(className);
        String fullPath = basePath.endsWith("/") ?
            basePath + classFile :
            basePath + "/" + classFile;
        return new Path(fullPath);
    }

    @Override
    public void close() throws IOException {
        if (hdfs != null) {
            hdfs.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String nameNodeUri;
        private String basePath = "/";
        private Configuration configuration;

        public Builder nameNodeUri(String nameNodeUri) {
            this.nameNodeUri = nameNodeUri;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = Objects.requireNonNull(basePath, "basePath cannot be null");
            return this;
        }

        public Builder configuration(Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        public HdfsClassSource build() throws IOException {
            Configuration conf = configuration != null ? configuration : new Configuration();

            if (nameNodeUri != null) {
                conf.set("fs.defaultFS", nameNodeUri);
            }

            FileSystem hdfs = FileSystem.get(conf);
            return new HdfsClassSource(hdfs, basePath);
        }
    }
}
