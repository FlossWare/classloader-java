package org.flossware.jclassloader;

import org.flossware.jclassloader.cache.ClassCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JClassLoader extends ClassLoader {
    private final List<ClassSource> classSources;
    private final ClassCache cache;
    private final boolean useCache;

    private JClassLoader(ClassLoader parent, List<ClassSource> classSources, ClassCache cache, boolean useCache) {
        super(parent);
        this.classSources = new ArrayList<>(classSources);
        this.cache = cache;
        this.useCache = useCache && cache != null;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classData = null;

        if (useCache && cache.contains(name)) {
            classData = cache.get(name);
            if (classData != null) {
                return defineClass(name, classData, 0, classData.length);
            }
        }

        for (ClassSource source : classSources) {
            try {
                if (source.canLoad(name)) {
                    classData = source.loadClassData(name);
                    if (classData != null) {
                        if (useCache) {
                            try {
                                cache.put(name, classData);
                            } catch (IOException e) {
                            }
                        }
                        return defineClass(name, classData, 0, classData.length);
                    }
                }
            } catch (IOException e) {
            }
        }

        throw new ClassNotFoundException(name);
    }

    public List<ClassSource> getClassSources() {
        return Collections.unmodifiableList(classSources);
    }

    public ClassCache getCache() {
        return cache;
    }

    public boolean isCacheEnabled() {
        return useCache;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ClassLoader parent;
        private final List<ClassSource> classSources = new ArrayList<>();
        private ClassCache cache;
        private boolean useCache = true;

        public Builder parent(ClassLoader parent) {
            this.parent = parent;
            return this;
        }

        public Builder addClassSource(ClassSource source) {
            Objects.requireNonNull(source, "source cannot be null");
            this.classSources.add(source);
            return this;
        }

        public Builder addLocalSource(String path) {
            return addClassSource(new LocalClassSource(path));
        }

        public Builder addRemoteSource(String baseUrl) {
            return addClassSource(new RemoteClassSource(baseUrl));
        }

        public Builder addRemoteSource(String baseUrl, AuthConfig authConfig) {
            return addClassSource(new RemoteClassSource(baseUrl, authConfig));
        }

        public Builder addNexusRawSource(String nexusUrl, String repository) {
            return addClassSource(new NexusClassSource(nexusUrl, repository, NexusClassSource.NexusMode.RAW));
        }

        public Builder addNexusRawSource(String nexusUrl, String repository, AuthConfig authConfig) {
            return addClassSource(new NexusClassSource(nexusUrl, repository, NexusClassSource.NexusMode.RAW, authConfig));
        }

        public Builder addNexusMavenSource(MavenNexusClassSource source) {
            return addClassSource(source);
        }

        public Builder addMavenCentral(String... artifactCoordinates) {
            MavenRepositoryClassSource.Builder builder = MavenRepositoryClassSource.builder()
                .mavenCentral();
            for (String coords : artifactCoordinates) {
                builder.addArtifact(coords);
            }
            return addClassSource(builder.build());
        }

        public Builder addMavenRepository(String repositoryUrl, String... artifactCoordinates) {
            MavenRepositoryClassSource.Builder builder = MavenRepositoryClassSource.builder()
                .repositoryUrl(repositoryUrl);
            for (String coords : artifactCoordinates) {
                builder.addArtifact(coords);
            }
            return addClassSource(builder.build());
        }

        public Builder addSftpSource(String host, String username, String password, String basePath) {
            return addClassSource(SftpClassSource.builder()
                .host(host)
                .username(username)
                .password(password)
                .basePath(basePath)
                .build());
        }

        public Builder addWebDavSource(String baseUrl) {
            return addClassSource(new WebDavClassSource(baseUrl));
        }

        public Builder addWebDavSource(String baseUrl, String username, String password) {
            return addClassSource(new WebDavClassSource(baseUrl, username, password));
        }

        public Builder addDatabaseSource(javax.sql.DataSource dataSource, String tableName,
                                        String classNameColumn, String classBytesColumn) {
            return addClassSource(new DatabaseClassSource(dataSource, tableName,
                                                         classNameColumn, classBytesColumn));
        }

        public Builder addRestApiSource(RestApiClassSource source) {
            return addClassSource(source);
        }

        public Builder addS3Source(org.flossware.jclassloader.cloud.S3ClassSource source) {
            return addClassSource(source);
        }

        public Builder addAzureBlobSource(org.flossware.jclassloader.cloud.AzureBlobClassSource source) {
            return addClassSource(source);
        }

        public Builder addGcsSource(org.flossware.jclassloader.cloud.GcsClassSource source) {
            return addClassSource(source);
        }

        public Builder addGoogleDriveSource(org.flossware.jclassloader.cloud.GoogleDriveClassSource source) {
            return addClassSource(source);
        }

        public Builder addDropboxSource(org.flossware.jclassloader.cloud.DropboxClassSource source) {
            return addClassSource(source);
        }

        public Builder addOneDriveSource(org.flossware.jclassloader.cloud.OneDriveClassSource source) {
            return addClassSource(source);
        }

        public Builder cache(ClassCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder useCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        public JClassLoader build() {
            if (classSources.isEmpty()) {
                throw new IllegalStateException("At least one class source must be configured");
            }

            ClassLoader parentLoader = parent != null ? parent : getSystemClassLoader();
            return new JClassLoader(parentLoader, classSources, cache, useCache);
        }
    }
}
