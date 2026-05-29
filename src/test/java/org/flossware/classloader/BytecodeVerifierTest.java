package org.flossware.classloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeVerifierTest {

    @Test
    void testFunctionalInterface() {
        // Test that BytecodeVerifier is a functional interface
        BytecodeVerifier verifier = (className, bytecode) -> {
            if (bytecode.length == 0) {
                throw new SecurityException("Empty bytecode");
            }
        };

        assertDoesNotThrow(() -> verifier.verify("Test", new byte[]{1, 2, 3}));
        assertThrows(SecurityException.class, () -> verifier.verify("Test", new byte[0]));
    }

    @Test
    void testCustomVerifierAcceptsValidBytecode() {
        BytecodeVerifier verifier = (className, bytecode) -> {
            // Verify magic number (0xCAFEBABE)
            if (bytecode.length < 4 ||
                bytecode[0] != (byte) 0xCA ||
                bytecode[1] != (byte) 0xFE ||
                bytecode[2] != (byte) 0xBA ||
                bytecode[3] != (byte) 0xBE) {
                throw new SecurityException("Invalid class file magic number");
            }
        };

        byte[] validBytecode = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 52};
        assertDoesNotThrow(() -> verifier.verify("ValidClass", validBytecode));
    }

    @Test
    void testCustomVerifierRejectsInvalidBytecode() {
        BytecodeVerifier verifier = (className, bytecode) -> {
            // Verify magic number
            if (bytecode.length < 4 ||
                bytecode[0] != (byte) 0xCA ||
                bytecode[1] != (byte) 0xFE ||
                bytecode[2] != (byte) 0xBA ||
                bytecode[3] != (byte) 0xBE) {
                throw new SecurityException("Invalid class file magic number");
            }
        };

        byte[] invalidBytecode = {0, 0, 0, 0};
        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            verifier.verify("InvalidClass", invalidBytecode);
        });

        assertTrue(thrown.getMessage().contains("Invalid class file magic number"));
    }

    @Test
    void testWhitelistVerifier() {
        Set<String> allowedClasses = new HashSet<>();
        allowedClasses.add("com.example.AllowedClass");
        allowedClasses.add("com.example.AnotherAllowedClass");

        BytecodeVerifier verifier = (className, bytecode) -> {
            if (!allowedClasses.contains(className)) {
                throw new SecurityException("Class not in whitelist: " + className);
            }
        };

        byte[] bytecode = new byte[]{1, 2, 3};

        assertDoesNotThrow(() -> verifier.verify("com.example.AllowedClass", bytecode));
        assertDoesNotThrow(() -> verifier.verify("com.example.AnotherAllowedClass", bytecode));

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            verifier.verify("com.example.ForbiddenClass", bytecode);
        });

        assertTrue(thrown.getMessage().contains("not in whitelist"));
    }

    @Test
    void testSizeLimitVerifier() {
        final int MAX_SIZE = 1024;

        BytecodeVerifier verifier = (className, bytecode) -> {
            if (bytecode.length > MAX_SIZE) {
                throw new SecurityException(
                    String.format("Bytecode too large: %d bytes (max %d)", bytecode.length, MAX_SIZE)
                );
            }
        };

        byte[] smallBytecode = new byte[512];
        assertDoesNotThrow(() -> verifier.verify("SmallClass", smallBytecode));

        byte[] largeBytecode = new byte[2048];
        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            verifier.verify("LargeClass", largeBytecode);
        });

        assertTrue(thrown.getMessage().contains("Bytecode too large"));
    }

    @Test
    void testVerifierInvocationCount() {
        AtomicInteger invocationCount = new AtomicInteger(0);

        BytecodeVerifier verifier = (className, bytecode) -> {
            invocationCount.incrementAndGet();
        };

        byte[] bytecode = new byte[]{1, 2, 3};

        verifier.verify("Class1", bytecode);
        verifier.verify("Class2", bytecode);
        verifier.verify("Class3", bytecode);

        assertEquals(3, invocationCount.get());
    }

    @Test
    void testPackageRestrictionVerifier() {
        BytecodeVerifier verifier = (className, bytecode) -> {
            if (className.startsWith("java.") || className.startsWith("javax.")) {
                throw new SecurityException("Cannot load JDK classes: " + className);
            }
        };

        byte[] bytecode = new byte[]{1, 2, 3};

        assertDoesNotThrow(() -> verifier.verify("com.example.MyClass", bytecode));

        SecurityException thrown1 = assertThrows(SecurityException.class, () -> {
            verifier.verify("java.lang.String", bytecode);
        });
        assertTrue(thrown1.getMessage().contains("Cannot load JDK classes"));

        SecurityException thrown2 = assertThrows(SecurityException.class, () -> {
            verifier.verify("javax.servlet.Servlet", bytecode);
        });
        assertTrue(thrown2.getMessage().contains("Cannot load JDK classes"));
    }

    @Test
    void testCompositeVerifier() {
        // Test combining multiple verifiers
        BytecodeVerifier magicNumberVerifier = (className, bytecode) -> {
            if (bytecode.length < 4 ||
                bytecode[0] != (byte) 0xCA ||
                bytecode[1] != (byte) 0xFE ||
                bytecode[2] != (byte) 0xBA ||
                bytecode[3] != (byte) 0xBE) {
                throw new SecurityException("Invalid magic number");
            }
        };

        BytecodeVerifier sizeVerifier = (className, bytecode) -> {
            if (bytecode.length > 1024) {
                throw new SecurityException("Too large");
            }
        };

        BytecodeVerifier compositeVerifier = (className, bytecode) -> {
            magicNumberVerifier.verify(className, bytecode);
            sizeVerifier.verify(className, bytecode);
        };

        byte[] validSmallBytecode = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0};
        assertDoesNotThrow(() -> compositeVerifier.verify("ValidClass", validSmallBytecode));

        byte[] invalidMagicNumber = {0, 0, 0, 0};
        assertThrows(SecurityException.class, () -> {
            compositeVerifier.verify("InvalidMagic", invalidMagicNumber);
        });

        byte[] validLargeBytecode = new byte[2048];
        validLargeBytecode[0] = (byte) 0xCA;
        validLargeBytecode[1] = (byte) 0xFE;
        validLargeBytecode[2] = (byte) 0xBA;
        validLargeBytecode[3] = (byte) 0xBE;
        assertThrows(SecurityException.class, () -> {
            compositeVerifier.verify("TooLarge", validLargeBytecode);
        });
    }

    @Test
    void testVerifierWithApplicationClassLoader(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        // Create a simple test class
        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("TestClass.java");
        Files.writeString(sourceFile, "public class TestClass { }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        assertEquals(0, pb.start().waitFor());

        // Verifier that rejects classes with "Forbidden" in the name
        BytecodeVerifier verifier = (className, bytecode) -> {
            if (className.contains("Forbidden")) {
                throw new SecurityException("Forbidden class name: " + className);
            }
        };

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .bytecodeVerifier(verifier)
            .build();

        // Should load successfully
        Class<?> loadedClass = loader.loadClass("TestClass");
        assertNotNull(loadedClass);
        assertEquals("TestClass", loadedClass.getSimpleName());
    }

    @Test
    void testVerifierRejectsByApplicationClassLoader(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);

        // Create a class with "Forbidden" in the name
        Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        Path sourceFile = sourceDir.resolve("ForbiddenClass.java");
        Files.writeString(sourceFile, "public class ForbiddenClass { }");

        ProcessBuilder pb = new ProcessBuilder("javac", "-d", classDir.toString(), sourceFile.toString());
        assertEquals(0, pb.start().waitFor());

        // Verifier that rejects classes with "Forbidden" in the name
        BytecodeVerifier verifier = (className, bytecode) -> {
            if (className.contains("Forbidden")) {
                throw new SecurityException("Forbidden class name: " + className);
            }
        };

        ApplicationClassLoader loader = ApplicationClassLoader.builder()
            .addLocalSource(classDir.toString())
            .bytecodeVerifier(verifier)
            .build();

        // Should throw ClassNotFoundException (wrapping SecurityException)
        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class, () -> {
            loader.loadClass("ForbiddenClass");
        });

        assertTrue(thrown.getMessage().contains("Bytecode verification failed") ||
                   thrown.getCause() instanceof SecurityException);
    }

    @Test
    void testNullClassName() {
        BytecodeVerifier verifier = (className, bytecode) -> {
            if (className == null) {
                throw new SecurityException("Class name cannot be null");
            }
        };

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            verifier.verify(null, new byte[]{1, 2, 3});
        });

        assertTrue(thrown.getMessage().contains("cannot be null"));
    }

    @Test
    void testNullBytecode() {
        BytecodeVerifier verifier = (className, bytecode) -> {
            if (bytecode == null) {
                throw new SecurityException("Bytecode cannot be null");
            }
        };

        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            verifier.verify("TestClass", null);
        });

        assertTrue(thrown.getMessage().contains("cannot be null"));
    }
}
