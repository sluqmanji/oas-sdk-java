package egain.oassdk.core.parser;

import egain.oassdk.core.exceptions.OASSDKException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

/**
 * Comprehensive tests for PathResolver
 */
public class PathResolverTest {
    
    @TempDir
    Path tempDir;
    
    private PathResolver pathResolver;
    private Path baseDir;
    private Path searchDir1;
    private Path searchDir2;
    
    @BeforeEach
    public void setUp() throws IOException {
        // Create test directory structure
        baseDir = tempDir.resolve("base");
        Files.createDirectories(baseDir);
        
        searchDir1 = tempDir.resolve("search1");
        Files.createDirectories(searchDir1);
        
        searchDir2 = tempDir.resolve("search2");
        Files.createDirectories(searchDir2);
        
        // Create test files
        Files.createFile(baseDir.resolve("test.yaml"));
        Files.createFile(searchDir1.resolve("schema.yaml"));
        Files.createFile(searchDir2.resolve("model.json"));
        
        // Create nested directory structure
        Path nestedDir = searchDir1.resolve("nested");
        Files.createDirectories(nestedDir);
        Files.createFile(nestedDir.resolve("nested.yaml"));
        
        // Initialize PathResolver with search paths
        List<String> searchPaths = List.of(
            searchDir1.toString(),
            searchDir2.toString()
        );
        pathResolver = new PathResolver(searchPaths);
    }
    
    @Test
    public void testResolveReferenceInBaseDir() throws OASSDKException {
        Path resolved = pathResolver.resolveReference("test.yaml", baseDir);
        assertEquals(baseDir.resolve("test.yaml"), resolved);
        assertTrue(Files.exists(resolved));
    }
    
    @Test
    public void testResolveReferenceInSearchPath() throws OASSDKException {
        Path resolved = pathResolver.resolveReference("schema.yaml", baseDir);
        assertEquals(searchDir1.resolve("schema.yaml"), resolved);
        assertTrue(Files.exists(resolved));
    }
    
    @Test
    public void testResolveReferenceNotFound() {
        assertThrows(OASSDKException.class, () -> {
            pathResolver.resolveReference("nonexistent.yaml", baseDir);
        });
    }
    
    @Test
    public void testPathTraversalProtection() {
        // Parent-relative refs are valid for OpenAPI $ref; missing targets must still fail
        assertThrows(OASSDKException.class, () ->
            pathResolver.resolveReference("../nonexistent-oas-ref-target.yaml", baseDir)
        );
    }

    @Test
    public void testParentRelativeRefOutsideBaseDir() throws IOException, OASSDKException {
        Path outsideFile = tempDir.resolve("outside.yaml");
        Files.createFile(outsideFile);

        Path resolved = pathResolver.resolveReference("../outside.yaml", baseDir);
        assertEquals(outsideFile.normalize(), resolved.normalize());
        assertTrue(Files.exists(resolved));
    }

    @Test
    public void testResolveRelativePathStringUnixAbsoluteBase() {
        assertEquals(
                "/tmp/a/c.yaml",
                PathResolver.resolveRelativePathString("/tmp/a/b", "../c.yaml"));
        assertEquals(
                "/tmp/junit/outside.yaml",
                PathResolver.resolveRelativePathString("/tmp/junit/base", "../outside.yaml"));
    }

    @Test
    public void testPathTraversalWithMultipleDots() {
        assertThrows(OASSDKException.class, () -> {
            pathResolver.resolveReference("../../../../../../../../nonexistent-oas-path-xyz.yaml", baseDir);
        });
    }
    
    @Test
    public void testInvalidFileExtension() {
        assertThrows(OASSDKException.class, () -> {
            pathResolver.resolveReference("test.txt", baseDir);
        });
    }
    
    @Test
    public void testNullFilePath() {
        assertThrows(OASSDKException.class, () -> {
            pathResolver.resolveReference(null, baseDir);
        });
    }
    
    @Test
    public void testEmptyFilePath() {
        assertThrows(OASSDKException.class, () -> {
            pathResolver.resolveReference("", baseDir);
        });
    }
    
    @Test
    public void testFileSizeLimit() throws IOException, OASSDKException {
        // Note: Actually creating a 100MB+ file would be slow, so we test the validation logic
        // In a real scenario, this would be tested with a mock or actual large file
        
        // For now, test that normal files work (file size validation happens in resolveReference)
        Path normalFile = pathResolver.resolveReference("test.yaml", baseDir);
        assertTrue(Files.exists(normalFile));
    }
    
    @Test
    public void testRecursiveFileSearch() throws IOException, OASSDKException {
        // File in nested directory should be found
        Path found = pathResolver.resolveReference("nested.yaml", baseDir);
        assertTrue(Files.exists(found));
        assertTrue(found.toString().contains("nested"));
    }
    
    @Test
    public void testMultipleSearchPaths() throws OASSDKException {
        // Should find file in first search path
        Path found1 = pathResolver.resolveReference("schema.yaml", baseDir);
        assertEquals(searchDir1.resolve("schema.yaml"), found1);
        
        // Should find file in second search path
        Path found2 = pathResolver.resolveReference("model.json", baseDir);
        assertEquals(searchDir2.resolve("model.json"), found2);
    }
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        // Test that multiple threads can use PathResolver concurrently
        int numThreads = 10;
        Thread[] threads = new Thread[numThreads];
        List<Exception> exceptions = new ArrayList<>();
        
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                try {
                    PathResolver resolver = new PathResolver(List.of(searchDir1.toString()));
                    for (int j = 0; j < 10; j++) {
                        resolver.resolveReference("schema.yaml", baseDir);
                        resolver.getCurrentRecursionDepth();
                        resolver.resetRecursionDepth();
                    }
                    resolver.cleanup();
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify no exceptions occurred
        assertTrue(exceptions.isEmpty(), "Thread safety test failed with exceptions: " + exceptions);
    }
    
    @Test
    public void testRecursionDepthTracking() throws OASSDKException {
        assertEquals(0, pathResolver.getCurrentRecursionDepth());
        
        pathResolver.resolveReference("test.yaml", baseDir);
        
        // Depth should reset after resolution
        assertEquals(0, pathResolver.getCurrentRecursionDepth());
    }
    
    @Test
    public void testResetRecursionDepth() {
        pathResolver.resetRecursionDepth();
        assertEquals(0, pathResolver.getCurrentRecursionDepth());
    }
    
    @Test
    public void testCleanup() {
        pathResolver.cleanup();
        // After cleanup, should still be able to use resolver
        assertDoesNotThrow(() -> {
            pathResolver.resolveReference("test.yaml", baseDir);
        });
    }
    
    @Test
    public void testDefaultConstructor() {
        PathResolver resolver = new PathResolver();
        assertNotNull(resolver);
        // Should work with environment variable or system property if set
    }
    
    @Test
    public void testPathSanitization() throws IOException, OASSDKException {
        // Test that directory paths with backslashes are normalized
        // Create a nested directory structure
        Path nestedDir = baseDir.resolve("subdir");
        Files.createDirectories(nestedDir);
        Path testFile = nestedDir.resolve("test.yaml");
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        Files.createFile(testFile);
        
        // Should handle both forward and backward slashes in directory paths
        Path resolved1 = pathResolver.resolveReference("subdir/test.yaml", baseDir);
        Path resolved2 = pathResolver.resolveReference("subdir\\test.yaml", baseDir);
        
        assertEquals(resolved1, resolved2);
        assertTrue(Files.exists(resolved1));
        assertTrue(Files.exists(resolved2));
    }
    
    @Test
    public void testNullBytesRemoval() throws IOException, OASSDKException {
        // Paths with null bytes should be sanitized (null bytes removed)
        Path testFile = baseDir.resolve("test.yaml");
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        Files.createFile(testFile);
        
        // Null bytes should be removed during sanitization, making the path valid
        Path resolved = pathResolver.resolveReference("test\0.yaml", baseDir);
        assertEquals(testFile, resolved);
        assertTrue(Files.exists(resolved));
    }
    
    @Test
    public void testAbsolutePathWithoutBaseDir() throws IOException, OASSDKException {
        Path absoluteFile = tempDir.resolve("absolute.yaml");
        Files.createFile(absoluteFile);
        
        // Should work with absolute paths when baseDir is null
        PathResolver resolver = new PathResolver();
        Path resolved = resolver.resolveReference(absoluteFile.toString(), null);
        assertTrue(Files.exists(resolved));
    }
    
    @Test
    public void testRelativePathWithoutBaseDir() {
        PathResolver resolver = new PathResolver();
        
        // Relative path without baseDir should fail
        assertThrows(OASSDKException.class, () -> {
            resolver.resolveReference("relative.yaml", null);
        });
    }
    
    @Test
    public void testMaxFileSizeConstant() {
        // Verify MAX_FILE_SIZE is accessible and has correct value
        assertEquals(100 * 1024 * 1024L, PathResolver.MAX_FILE_SIZE);
    }
}

