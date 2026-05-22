package org.flossware.jclassloader.vcs;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.flossware.jclassloader.ClassSource;

import java.io.File;
import java.io.IOException;

/**
 * ClassSource implementation for loading classes from Git repositories.
 * Loads classes from a specific commit or branch in a Git repository.
 * Requires the JGit library dependency.
 */
import java.util.Objects;

/**
 * Loads classes from a Git repository.
 * Can load from local Git repos or clone from remote URLs.
 */
public class GitClassSource implements ClassSource {
    private final Repository repository;
    private final String branch;
    private final String basePath;

    private GitClassSource(Repository repository, String branch, String basePath) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.branch = branch != null ? branch : "main";
        this.basePath = basePath != null ? basePath : "";
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        String classPath = buildClassPath(className);

        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId lastCommitId = repository.resolve("refs/heads/" + branch);
            if (lastCommitId == null) {
                // Try master if main doesn't exist
                lastCommitId = repository.resolve("refs/heads/master");
            }

            if (lastCommitId == null) {
                throw new IOException("Branch not found: " + branch);
            }

            RevCommit commit = revWalk.parseCommit(lastCommitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    if (treeWalk.getPathString().equals(classPath)) {
                        ObjectId objectId = treeWalk.getObjectId(0);
                        ObjectLoader loader = repository.open(objectId);
                        return loader.getBytes();
                    }
                }
            }

            throw new IOException("Class file not found in Git: " + classPath);
        }
    }

    @Override
    public boolean canLoad(String className) {
        try {
            loadClassData(className);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "GitClassSource[branch=" + branch + ", basePath=" + basePath + "]";
    }

    private String buildClassPath(String className) {
        String classFile = className.replace('.', '/') + ".class";

        if (basePath.isEmpty()) {
            return classFile;
        }

        String normalizedBase = basePath.endsWith("/") ?
            basePath.substring(0, basePath.length() - 1) :
            basePath;

        return normalizedBase + "/" + classFile;
    }

    public void close() {
        if (repository != null) {
            repository.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String repositoryPath;
        private String remoteUrl;
        private String branch = "main";
        private String basePath = "";
        private File cloneDirectory;

        public Builder repositoryPath(String repositoryPath) {
            this.repositoryPath = repositoryPath;
            return this;
        }

        public Builder remoteUrl(String remoteUrl) {
            this.remoteUrl = remoteUrl;
            return this;
        }

        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder basePath(String basePath) {
            this.basePath = basePath;
            return this;
        }

        public Builder cloneDirectory(File cloneDirectory) {
            this.cloneDirectory = cloneDirectory;
            return this;
        }

        public GitClassSource build() throws Exception {
            Repository repository;

            if (remoteUrl != null) {
                // Clone remote repository
                File cloneDir = cloneDirectory != null ? cloneDirectory :
                    new File(System.getProperty("java.io.tmpdir"), "jclassloader-git-" + System.currentTimeMillis());

                Git git = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(cloneDir)
                    .setBranch(branch)
                    .call();

                repository = git.getRepository();
            } else if (repositoryPath != null) {
                // Use local repository
                FileRepositoryBuilder builder = new FileRepositoryBuilder();
                repository = builder.setGitDir(new File(repositoryPath, ".git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
            } else {
                throw new IllegalStateException("Either repositoryPath or remoteUrl must be set");
            }

            return new GitClassSource(repository, branch, basePath);
        }
    }
}
