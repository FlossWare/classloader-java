package org.flossware.jclassloader;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bytecode verifier that validates checksums against expected values.
 * Supports SHA-256 checksums for integrity verification.
 */
public final class ChecksumValidator implements BytecodeVerifier {
    private final Map<String, String> checksums;
    private final String algorithm;

    /**
     * Creates a checksum validator with the specified checksums and algorithm.
     *
     * @param checksums Map of class names to expected checksums (hex strings)
     * @param algorithm The hash algorithm to use (e.g., "SHA-256")
     * @throws NullPointerException if checksums or algorithm is null
     */
    public ChecksumValidator(Map<String, String> checksums, String algorithm) {
        Objects.requireNonNull(checksums, "checksums cannot be null");
        Objects.requireNonNull(algorithm, "algorithm cannot be null");
        this.checksums = new HashMap<>(checksums);
        this.algorithm = algorithm;
    }

    /**
     * Creates a checksum validator using SHA-256.
     *
     * @param checksums Map of class names to expected SHA-256 checksums (hex strings)
     */
    public ChecksumValidator(Map<String, String> checksums) {
        this(checksums, "SHA-256");
    }

    @Override
    public void verify(String className, byte[] bytecode) throws SecurityException {
        String expected = checksums.get(className);
        if (expected == null) {
            throw new SecurityException("No checksum found for class: " + className);
        }

        String actual;
        try {
            actual = calculateChecksum(bytecode);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("Checksum algorithm not available: " + algorithm, e);
        }

        if (!expected.equalsIgnoreCase(actual)) {
            throw new SecurityException(
                String.format("Checksum mismatch for %s: expected %s, got %s",
                    className, expected, actual)
            );
        }
    }

    /**
     * Calculates the checksum for the given bytecode.
     *
     * @param bytecode The bytecode to hash
     * @return The hex-encoded checksum
     * @throws NoSuchAlgorithmException if the algorithm is not available
     */
    private String calculateChecksum(byte[] bytecode) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hash = digest.digest(bytecode);
        return bytesToHex(hash);
    }

    /**
     * Converts a byte array to a hex string.
     *
     * @param bytes The bytes to convert
     * @return The hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Gets the checksums map.
     *
     * @return Unmodifiable view of the checksums map
     */
    public Map<String, String> getChecksums() {
        return Collections.unmodifiableMap(checksums);
    }

    /**
     * Gets the hash algorithm.
     *
     * @return The hash algorithm name
     */
    public String getAlgorithm() {
        return algorithm;
    }
}
