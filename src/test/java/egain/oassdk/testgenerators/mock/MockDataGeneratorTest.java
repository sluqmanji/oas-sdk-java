package egain.oassdk.testgenerators.mock;

import egain.oassdk.config.TestConfig;
import egain.oassdk.core.exceptions.GenerationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockDataGenerator.
 */
public class MockDataGeneratorTest {

    @Test
    public void testGetName() {
        MockDataGenerator generator = new MockDataGenerator();
        assertNotNull(generator.getName());
    }

    @Test
    public void testGenerateWithMinimalSpecCreatesOutput(@TempDir Path tempDir) throws GenerationException {
        MockDataGenerator generator = new MockDataGenerator();
        Map<String, Object> spec = Map.of(
                "info", Map.of("title", "API", "version", "1.0"),
                "paths", Map.of(),
                "components", Map.of("schemas", Map.of("User", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string")))))
        );
        generator.generate(spec, tempDir.toString(), new TestConfig(), "java");
        assertTrue(Files.exists(tempDir));
    }
}
