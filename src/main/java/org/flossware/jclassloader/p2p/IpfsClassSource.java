package org.flossware.jclassloader.p2p;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import org.flossware.jclassloader.ClassSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads classes from IPFS (InterPlanetary File System).
 * Classes can be accessed by CID (Content Identifier) or via a configured root directory.
 */
public class IpfsClassSource implements ClassSource {
    private final IPFS ipfs;
    private final String rootCid;  // Optional: root directory CID
    private final Map<String, String> classNameToCidMap;  // Optional: direct class to CID mapping

    private IpfsClassSource(IPFS ipfs, String rootCid, Map<String, String> classNameToCidMap) {
        this.ipfs = Objects.requireNonNull(ipfs, "ipfs cannot be null");
        this.rootCid = rootCid;
        this.classNameToCidMap = classNameToCidMap != null ? classNameToCidMap : new HashMap<>();
    }

    @Override
    public byte[] loadClassData(String className) throws IOException {
        try {
            // Check direct CID mapping first
            if (classNameToCidMap.containsKey(className)) {
                String cid = classNameToCidMap.get(className);
                return loadFromCid(cid);
            }

            // Try to load from root directory structure
            if (rootCid != null) {
                String classPath = className.replace('.', '/') + ".class";
                String fullPath = rootCid + "/" + classPath;
                return loadFromPath(fullPath);
            }

            throw new IOException("No CID mapping or root directory configured for class: " + className);

        } catch (Exception e) {
            throw new IOException("Failed to load class from IPFS: " + className, e);
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
        return "IpfsClassSource[rootCid=" + rootCid +
               ", mappedClasses=" + classNameToCidMap.size() + "]";
    }

    private byte[] loadFromCid(String cid) throws IOException {
        Multihash hash = Multihash.fromBase58(cid);
        return ipfs.cat(hash);
    }

    private byte[] loadFromPath(String path) throws IOException {
        return ipfs.cat(Multihash.fromBase58(path));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String ipfsHost = "127.0.0.1";
        private int ipfsPort = 5001;
        private String rootCid;
        private final Map<String, String> classNameToCidMap = new HashMap<>();

        public Builder ipfsHost(String ipfsHost) {
            this.ipfsHost = ipfsHost;
            return this;
        }

        public Builder ipfsPort(int ipfsPort) {
            this.ipfsPort = ipfsPort;
            return this;
        }

        public Builder rootCid(String rootCid) {
            this.rootCid = rootCid;
            return this;
        }

        public Builder mapClass(String className, String cid) {
            this.classNameToCidMap.put(className, cid);
            return this;
        }

        public Builder mapClasses(Map<String, String> mappings) {
            this.classNameToCidMap.putAll(mappings);
            return this;
        }

        public IpfsClassSource build() {
            IPFS ipfs = new IPFS(ipfsHost, ipfsPort);
            return new IpfsClassSource(ipfs, rootCid, classNameToCidMap);
        }
    }
}
