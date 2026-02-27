package egain.oassdk.test.sequence;

import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RandomizedSequenceTester.
 */
public class RandomizedSequenceTesterTest {

    @Test
    public void testGenerateSequenceTestsNullOutputDirThrows() {
        RandomizedSequenceTester generator = new RandomizedSequenceTester();
        assertThrows(Exception.class, () ->
                generator.generateSequenceTests(Map.of(), null, "http://localhost:8080")
        );
    }

    @Test
    public void testGenerateSequenceTestsCreatesFiles(@TempDir Path tempDir) throws GenerationException {
        RandomizedSequenceTester generator = new RandomizedSequenceTester();
        Map<String, Object> spec = Map.of("paths", Map.of());
        generator.generateSequenceTests(spec, tempDir.toString(), "http://localhost:8080");
        assertTrue(Files.exists(tempDir.resolve("SequenceTestFramework.java")));
        assertTrue(Files.exists(tempDir.resolve("RandomSequenceGenerator.java")));
    }
}
