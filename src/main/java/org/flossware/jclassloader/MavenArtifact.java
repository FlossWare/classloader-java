package org.flossware.jclassloader;

import java.util.Objects;

public class MavenArtifact {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String packaging;

    public MavenArtifact(String groupId, String artifactId, String version, String classifier, String packaging) {
        this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.classifier = classifier;
        this.packaging = packaging != null ? packaging : "jar";
    }

    public MavenArtifact(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null, "jar");
    }

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

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

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
