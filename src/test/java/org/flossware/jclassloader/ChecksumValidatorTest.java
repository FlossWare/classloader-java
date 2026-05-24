package org.flossware.jclassloader;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumValidatorTest {

    private static final byte[] TEST_BYTECODE = "public class Test {}".getBytes();

    private String calculateSHA256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    void testValidChecksumPasses() throws Exception {
        String expectedChecksum = calculateSHA256(TEST_BYTECODE);

        Map<String, String> checksums = new HashMap<>();
        checksums.put("TestClass", expectedChecksum);

        ChecksumValidator validator = new ChecksumValidator(checksums);

        // Should not throw
        assertDoesNotThrow(() -> validator.verify("TestClass", TEST_BYTECODE));
    }

    @Test
    void testInvalidChecksumFails() throws Exception {
        Map<String, String> checksums = new HashMap<>();
        checksums.put("TestClass", "invalidchecksumvalue");

        ChecksumValidator validator = new ChecksumValidator(checksums);

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            validator.verify("TestClass", TEST_BYTECODE);
        });

        assertTrue(thrown.getMessage().contains("Checksum mismatch"));
        assertTrue(thrown.getMessage().contains("TestClass"));
        assertTrue(thrown.getMessage().contains("expected"));
        assertTrue(thrown.getMessage().contains("got"));
    }

    @Test
    void testMissingChecksumFails() {
        Map<String, String> checksums = new HashMap<>();
        ChecksumValidator validator = new ChecksumValidator(checksums);

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            validator.verify("UnknownClass", TEST_BYTECODE);
        });

        assertTrue(thrown.getMessage().contains("No checksum found"));
        assertTrue(thrown.getMessage().contains("UnknownClass"));
    }

    @Test
    void testChecksumCaseInsensitive() throws Exception {
        String checksum = calculateSHA256(TEST_BYTECODE);

        Map<String, String> checksums = new HashMap<>();
        checksums.put("TestClass", checksum.toUpperCase());

        ChecksumValidator validator = new ChecksumValidator(checksums);

        // Should not throw - checksums are case-insensitive
        assertDoesNotThrow(() -> validator.verify("TestClass", TEST_BYTECODE));
    }

    @Test
    void testModifiedBytecodeFails() throws Exception {
        String originalChecksum = calculateSHA256(TEST_BYTECODE);

        Map<String, String> checksums = new HashMap<>();
        checksums.put("TestClass", originalChecksum);

        ChecksumValidator validator = new ChecksumValidator(checksums);

        // Modify the bytecode
        byte[] modifiedBytecode = TEST_BYTECODE.clone();
        modifiedBytecode[0] = (byte) (modifiedBytecode[0] + 1);

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            validator.verify("TestClass", modifiedBytecode);
        });

        assertTrue(thrown.getMessage().contains("Checksum mismatch"));
    }

    @Test
    void testMultipleClasses() throws Exception {
        byte[] bytecode1 = "class One {}".getBytes();
        byte[] bytecode2 = "class Two {}".getBytes();

        String checksum1 = calculateSHA256(bytecode1);
        String checksum2 = calculateSHA256(bytecode2);

        Map<String, String> checksums = new HashMap<>();
        checksums.put("ClassOne", checksum1);
        checksums.put("ClassTwo", checksum2);

        ChecksumValidator validator = new ChecksumValidator(checksums);

        // Both should pass
        assertDoesNotThrow(() -> validator.verify("ClassOne", bytecode1));
        assertDoesNotThrow(() -> validator.verify("ClassTwo", bytecode2));

        // Cross-verification should fail
        assertThrows(SecurityException.class, () -> {
            validator.verify("ClassOne", bytecode2);
        });
    }

    @Test
    void testCustomAlgorithmMD5() throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] hash = md5.digest(TEST_BYTECODE);
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        String md5Checksum = hexString.toString();

        Map<String, String> checksums = new HashMap<>();
        checksums.put("TestClass", md5Checksum);

        ChecksumValidator validator = new ChecksumValidator(checksums, "MD5");

        assertDoesNotThrow(() -> validator.verify("TestClass", TEST_BYTECODE));
    }

    @Test
    void testInvalidAlgorithmFails() {
        Map<String, String> checksums = new HashMap<>();
        checksums.put("TestClass", "somehash");

        ChecksumValidator validator = new ChecksumValidator(checksums, "INVALID-ALGORITHM");

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            validator.verify("TestClass", TEST_BYTECODE);
        });

        assertTrue(thrown.getMessage().contains("Checksum algorithm not available"));
        assertTrue(thrown.getMessage().contains("INVALID-ALGORITHM"));
    }

    @Test
    void testGettersReturnExpectedValues() {
        Map<String, String> checksums = new HashMap<>();
        checksums.put("Class1", "hash1");
        checksums.put("Class2", "hash2");

        ChecksumValidator validator = new ChecksumValidator(checksums, "SHA-256");

        assertEquals("SHA-256", validator.getAlgorithm());

        Map<String, String> returnedChecksums = validator.getChecksums();
        assertEquals(2, returnedChecksums.size());
        assertEquals("hash1", returnedChecksums.get("Class1"));
        assertEquals("hash2", returnedChecksums.get("Class2"));

        // Should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedChecksums.put("Class3", "hash3");
        });
    }

    @Test
    void testConstructorNullChecksums() {
        assertThrows(NullPointerException.class, () -> {
            new ChecksumValidator(null);
        });

        assertThrows(NullPointerException.class, () -> {
            new ChecksumValidator(null, "SHA-256");
        });
    }

    @Test
    void testConstructorNullAlgorithm() {
        Map<String, String> checksums = new HashMap<>();
        assertThrows(NullPointerException.class, () -> {
            new ChecksumValidator(checksums, null);
        });
    }

    @Test
    void testEmptyBytecode() throws Exception {
        byte[] emptyBytecode = new byte[0];
        String emptyChecksum = calculateSHA256(emptyBytecode);

        Map<String, String> checksums = new HashMap<>();
        checksums.put("EmptyClass", emptyChecksum);

        ChecksumValidator validator = new ChecksumValidator(checksums);

        assertDoesNotThrow(() -> validator.verify("EmptyClass", emptyBytecode));
    }

    @Test
    void testLargeBytecode() throws Exception {
        // Test with 1MB bytecode
        byte[] largeBytecode = new byte[1024 * 1024];
        for (int i = 0; i < largeBytecode.length; i++) {
            largeBytecode[i] = (byte) (i % 256);
        }

        String largeChecksum = calculateSHA256(largeBytecode);

        Map<String, String> checksums = new HashMap<>();
        checksums.put("LargeClass", largeChecksum);

        ChecksumValidator validator = new ChecksumValidator(checksums);

        assertDoesNotThrow(() -> validator.verify("LargeClass", largeBytecode));
    }

    @Test
    void testSHA256DefaultAlgorithm() {
        Map<String, String> checksums = new HashMap<>();
        ChecksumValidator validator = new ChecksumValidator(checksums);

        assertEquals("SHA-256", validator.getAlgorithm());
    }
}
