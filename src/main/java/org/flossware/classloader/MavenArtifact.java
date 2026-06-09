package org.flossware.classloader;

import java.util.Objects;

/**
 * Represents a Maven artifact with its coordinates (groupId, artifactId, version, classifier, packaging).
 * Provides parsing and path resolution for Maven artifacts.
 * Immutable value object with proper equals/hashCode/toString implementations.
 */
public final class MavenArtifact {
    private static final int MIN_COORDINATE_PARTS = 3;
    private static final int MAX_COORDINATE_PARTS = 5;
    private static final int INDEX_GROUP_ID = 0;
    private static final int INDEX_ARTIFACT_ID = 1;
    private static final int INDEX_VERSION = 2;
    private static final int INDEX_CLASSIFIER = 3;
    private static final int INDEX_PACKAGING = 4;

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
     * @throws NullPointerException if groupId, artifactId, or version is null
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

        if (parts.length < MIN_COORDINATE_PARTS || parts.length > MAX_COORDINATE_PARTS) {
            throw new IllegalArgumentException(
                "Invalid Maven coordinates: " + coordinates
                + ". Expected: groupId:artifactId:version[:classifier][:packaging]");
        }

        String groupId = parts[INDEX_GROUP_ID];
        String artifactId = parts[INDEX_ARTIFACT_ID];
        String version = parts[INDEX_VERSION];
        String classifier = parts.length > INDEX_CLASSIFIER ? parts[INDEX_CLASSIFIER] : null;
        String packaging = parts.length > INDEX_PACKAGING ? parts[INDEX_PACKAGING] : "jar";

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

    /**
     * Returns a string representation of this artifact in Maven coordinates format.
     * Format: "groupId:artifactId:version[:classifier][:packaging]"
     *
     * @return the Maven coordinates string
     */
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

    /**
     * Compares this artifact with another object for equality.
     * Two MavenArtifact instances are equal if they have the same groupId, artifactId,
     * version, classifier, and packaging.
     *
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenArtifact that = (MavenArtifact) o;
        return Objects.equals(groupId, that.groupId)
            && Objects.equals(artifactId, that.artifactId)
            && Objects.equals(version, that.version)
            && Objects.equals(classifier, that.classifier)
            && Objects.equals(packaging, that.packaging);
    }

    /**
     * Returns a hash code value for this artifact based on all coordinate fields.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, packaging);
    }
}
