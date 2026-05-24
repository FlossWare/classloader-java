package org.flossware.jclassloader.vcs;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GitClassSource builder and configuration.
 */
class GitClassSourceTest {

    @Test
    void testBuilderWithRepositoryPath() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .build();

        assertNotNull(source);
        assertNotNull(source.getDescription());
    }

    @Test
    void testBuilderWithRemoteUrlRequiresClone() {
        // Remote URL cloning requires actual network call, test only builder configuration
        GitClassSource.Builder builder = GitClassSource.builder()
                .remoteUrl("https://github.com/example/repo.git");

        assertNotNull(builder);
    }

    @Test
    void testBuilderWithBranch() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .branch("develop")
                .build();

        assertTrue(source.getDescription().contains("branch=develop"));
    }

    @Test
    void testBuilderWithBasePath() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .basePath("src/main/java")
                .build();

        assertTrue(source.getDescription().contains("basePath=src/main/java"));
    }

    @Test
    void testBuilderWithCloneDirectory() {
        File cloneDir = new File("/tmp/git-clone-test");
        GitClassSource.Builder builder = GitClassSource.builder()
                .cloneDirectory(cloneDir);

        assertNotNull(builder);
    }

    @Test
    void testBuilderMissingPathAndUrlThrowsException() {
        assertThrows(IllegalStateException.class, () -> {
            GitClassSource.builder().build();
        });
    }

    @Test
    void testBuilderDefaultBranch() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .build();

        assertTrue(source.getDescription().contains("branch=main"));
    }

    @Test
    void testBuilderDefaultBasePath() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .build();

        assertTrue(source.getDescription().contains("basePath="));
    }

    @Test
    void testGetDescription() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .branch("main")
                .basePath("classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("GitClassSource"));
        assertTrue(description.contains("branch=main"));
        assertTrue(description.contains("basePath=classes"));
    }

    @Test
    void testGetDescriptionWithDefaultValues() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("GitClassSource"));
        assertTrue(description.contains("branch=main"));
    }

    @Test
    void testCloseDoesNotThrow() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .build();

        assertDoesNotThrow(() -> source.close());
    }

    @Test
    void testMultipleClose() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .build();

        assertDoesNotThrow(() -> {
            source.close();
            source.close();
        });
    }

    @Test
    void testBuilderWithCustomBranch() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .branch("feature/test")
                .build();

        assertTrue(source.getDescription().contains("branch=feature/test"));
    }

    @Test
    void testBuilderWithRemoteUrlAndBranch() {
        // Remote URL cloning requires actual network call, test only builder configuration
        GitClassSource.Builder builder = GitClassSource.builder()
                .remoteUrl("https://github.com/example/repo.git")
                .branch("develop");

        assertNotNull(builder);
    }

    @Test
    void testBuilderWithAllOptions() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .branch("develop")
                .basePath("src/classes")
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("branch=develop"));
        assertTrue(description.contains("basePath=src/classes"));
    }

    @Test
    void testBuilderReturnsBuilder() {
        GitClassSource.Builder builder = GitClassSource.builder();
        assertSame(builder, builder.repositoryPath("/path"));
        assertSame(builder, builder.branch("main"));
        assertSame(builder, builder.basePath("classes"));
        assertSame(builder, builder.remoteUrl("https://example.com/repo.git"));
        assertSame(builder, builder.cloneDirectory(new File("/tmp")));
    }

    @Test
    void testBuilderWithNullBranch() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .branch(null)
                .build();

        assertTrue(source.getDescription().contains("branch=main"));
    }

    @Test
    void testBuilderWithNullBasePath() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .basePath(null)
                .build();

        String description = source.getDescription();
        assertTrue(description.contains("basePath="));
    }

    @Test
    void testMultipleInstances() throws Exception {
        GitClassSource source1 = GitClassSource.builder()
                .repositoryPath("/path/to/repo1")
                .branch("main")
                .build();

        GitClassSource source2 = GitClassSource.builder()
                .repositoryPath("/path/to/repo2")
                .branch("develop")
                .build();

        assertNotEquals(source1.getDescription(), source2.getDescription());
    }

    @Test
    void testBuilderWithEmptyBasePath() throws Exception {
        GitClassSource source = GitClassSource.builder()
                .repositoryPath("/path/to/repo")
                .basePath("")
                .build();

        assertTrue(source.getDescription().contains("basePath="));
    }
}
