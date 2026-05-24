package org.flossware.jclassloader;

import java.util.Objects;

/**
 * Represents a Maven artifact with its coordinates (groupId, artifactId, version, classifier, packaging).
 * Provides parsing and path resolution for Maven artifacts.
 * Immutable value object with proper equals/hashCode/toString implementations.
 */
public final class MavenArtifact {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String packaging;

    /**
     * Creates a Maven artifact with full coordinates.
     *
     * @param groupId The Maven group ID (e.g., "org.apache.commons")
     * @param artifactId The Maven artifact ID (e.g., "commons-lang3")
     * @param version The version (e.g., "3.12.0")
     * @param classifier Optional classifier (e.g., "sources", null for none)
     * @param packaging The packaging type (e.g., "jar", "war")
     * @throws NullPointerException if groupId, artifactId, or version is null
     */
    public MavenArtifact(String groupId, String artifactId, String version, String classifier, String packaging) {
        this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.classifier = classifier;
        this.packaging = packaging != null ? packaging : "jar";
    }

    /**
     * Creates a Maven artifact with standard JAR packaging and no classifier.
     *
     * @param groupId The Maven group ID
     * @param artifactId The Maven artifact ID
     * @param version The version
     */
    public MavenArtifact(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null, "jar");
    }

    /**
     * Parses Maven coordinates string into a MavenArtifact.
     * Accepts formats: groupId:artifactId:version[:classifier][:packaging]
     *
     * @param coordinates The Maven coordinates string (e.g., "org.apache.commons:commons-lang3:3.12.0")
     * @return The parsed MavenArtifact
     * @throws NullPointerException if coordinates is null
     * @throws IllegalArgumentException if coordinates format is invalid
     */
    public static MavenArtifact parse(String coordinates) {
        Objects.requireNonNull(coordinates, "coordinates cannot be null");
        String[] parts = coordinates.split(":");

        if (parts.length < 3 || parts.length > 5) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates +
                ". Expected format: groupId:artifactId:version[:classifier][:packaging]");
        }

        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;
        String packaging = parts.length > 4 ? parts[4] : "jar";

        return new MavenArtifact(groupId, artifactId, version, classifier, packaging);
    }

    /**
     * Converts this artifact to a Maven repository path.
     * Example: org.apache.commons:commons-lang3:3.12.0 becomes
     * org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar
     *
     * @return The repository path for this artifact
     */
    public String toPath() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId.replace('.', '/')).append('/');
        sb.append(artifactId).append('/');
        sb.append(version).append('/');
        sb.append(artifactId).append('-').append(version);

        if (classifier != null && !classifier.isEmpty()) {
            sb.append('-').append(classifier);
        }

        sb.append('.').append(packaging);

        return sb.toString();
    }

    /**
     * Gets the Maven group ID.
     *
     * @return The group ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the Maven artifact ID.
     *
     * @return The artifact ID
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Gets the artifact version.
     *
     * @return The version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the classifier (e.g., "sources", "javadoc").
     *
     * @return The classifier, or null if none
     */
    public String getClassifier() {
        return classifier;
    }

    /**
     * Gets the packaging type (e.g., "jar", "war").
     *
     * @return The packaging type
     */
    public String getPackaging() {
        return packaging;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(':').append(artifactId).append(':').append(version);
        if (classifier != null && !classifier.isEmpty()) {
            sb.append(':').append(classifier);
        }
        if (!"jar".equals(packaging)) {
            sb.append(':').append(packaging);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenArtifact that = (MavenArtifact) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(artifactId, that.artifactId) &&
               Objects.equals(version, that.version) &&
               Objects.equals(classifier, that.classifier) &&
               Objects.equals(packaging, that.packaging);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, packaging);
    }
}
